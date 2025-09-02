package uz.zero_one.chat

import jakarta.transaction.Transactional
import org.springframework.context.ApplicationListener
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent
import org.springframework.web.socket.messaging.SessionConnectEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Base64
import java.util.Date
import java.util.UUID

interface UserService {
    fun login(request: TelegramLoginRequestDto): JwtResponseDto
    fun updateUser(id: Long, request: UserUpdateRequestDto, file: MultipartFile?)
    fun deleteUser(id: Long)
    fun getAllUsersByUsernameSearch(search: String?, pageable: Pageable): Page<GetOneUserResponseDto>
    fun getUserStatus(id: Long): UserStatusDto
    fun tokenMe(): GetOneUserResponseDto
}

interface ChatService {
    fun createPrivateChat(senderId: Long, receiverId: Long): Chat
    fun sendMessage(messageDto: MessageRequestDto, username: String)
    fun createPublicChat(requestDto: CreatePublicChatRequestDto): GetOneChatResponseDto
    fun addMembers(chatId: Long, requestDto: AddMembersRequestDto?)
    fun markMessagesAsRead(dto: ReadMessageRequestDto, username: String)
    fun getMessage(chatId: Long, lastMessageId: Long?, pageable: Pageable): Page<MessageResponseDto>
    fun editMessage(chatId: Long, messageId: Long, newContent: String?, username: String)
    fun getChatList(pageable: Pageable): Page<ChatListResponseDto>
    fun deleteMessage(requestDto: DeleteMessageRequestDto, username: String)
    fun getGroupChatDetails(chatId: Long): GroupChatResponseDto
    fun deleteChat(requestDto: DeleteChatRequestDto, username: String)
    fun getChatType(chatType: ChatType,pageable: Pageable): Page<ChatListResponseDto>
}

@Service
class UserServiceImpl(
    private val telegramAuthValidator: TelegramAuthValidator,
    private val userRepository: UserRepository,
    private val jwtService: JwtService
) : UserService {

    override fun login(request: TelegramLoginRequestDto): JwtResponseDto {
        if (!telegramAuthValidator.validate(request)) {
            throw InvalidTelegramDataException(request.hash)
        }
        val user = userRepository.findByTelegramIdAndDeletedFalse(request.telegramId)
            ?: userRepository.save(
                User(
                    firstName = request.firstName ?: "No name",
                    telegramId = request.telegramId,
                    username = request.username ?: "unknown_${request.telegramId}",
                    avatarUrl = null,
                    authDate = request.authDate,
                    bio = null
                )
            )

        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = jwtService.generateRefreshToken(user)
        println("Accsess Token = $accessToken")
        return JwtResponseDto(accessToken, refreshToken)
    }

    override fun updateUser(id: Long, request: UserUpdateRequestDto, file: MultipartFile?) {
        val user = userRepository.findByIdAndDeletedFalse(id) ?: throw UserNotFoundException(id)
        request.firstName?.let { user.firstName = it }
        request.username?.let { user.username = it }
        request.bio?.let { user.bio = it }
        println("")
        if (file != null && !file.isEmpty) {
            val extension = file.originalFilename?.substringAfterLast(".")?.lowercase()
            val newFileName = "${UUID.randomUUID()}.${extension}"
            val uploadDir = "uploads/avatars"
            val dest = File(uploadDir, newFileName)
            dest.parentFile.mkdirs()
            file.transferTo(dest)
            user.avatarUrl = dest.path
        }
        userRepository.save(user)
    }

    override fun deleteUser(id: Long) {
        userRepository.trash(id) ?: throw UserNotFoundException(id)
    }

    override fun getAllUsersByUsernameSearch(
        search: String?,
        pageable: Pageable
    ): Page<GetOneUserResponseDto> {
        val users = userRepository.findAllUsersByUsernameSearch(search, pageable)
        return users.map { dto -> GetOneUserResponseDto.toResponse(dto) }
    }

    override fun getUserStatus(id: Long): UserStatusDto {
        val user = userRepository.findByIdAndDeletedFalse(id) ?: throw UserNotFoundException(id)
        return UserStatusDto(user.status, user.lastSeen)
    }

    override fun tokenMe(): GetOneUserResponseDto {
        val currentUserId = getCurrentUserId()
        val user = userRepository.findByIdAndDeletedFalse(currentUserId) ?: throw UserNotFoundException(currentUserId)
        return GetOneUserResponseDto.toResponse(user)
    }

    fun getAuthenticationFromToken(token: String): Authentication {
        val claims = jwtService.accessTokenClaims(token)
        val username = claims.subject
        val authorities = emptyList<GrantedAuthority>()
        val auth = UsernamePasswordAuthenticationToken(username, token, authorities)
        auth.details = (claims["userId"] as Number).toLong()
        return auth
    }

}

@Service
class ChatServiceImpl(
    private val chatRepository: ChatRepository,
    private val chatMemberRepository: ChatMemberRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val simpleMessagingTemplate: SimpMessagingTemplate,
    private val messageStatusRepository: MessageStatusRepository,
    private val fileRepository: FileRepository
) : ChatService {

    @Transactional
    override fun createPrivateChat(senderId: Long, receiverId: Long): Chat {
        val existing = chatRepository.findPrivateChat(receiverId, senderId)
        if (existing != null) return existing
        val sender = userRepository.findByIdAndDeletedFalse(senderId) ?: throw UserNotFoundException(senderId)
        val user = userRepository.findByIdAndDeletedFalse(receiverId) ?: throw UserNotFoundException(receiverId)

        val chat = chatRepository.save(
            Chat(chatType = ChatType.PRIVATE, null, null, null)
        )
        chatMemberRepository.save(ChatMember(chat = chat, user = sender))
        chatMemberRepository.save(ChatMember(chat = chat, user = user))
        return chat
    }

    @Transactional
    override fun sendMessage(messageDto: MessageRequestDto, username: String) {
        println("sendmessage")
        val sender = userRepository.findByUsernameAndDeletedFalse(username) ?: throw UsernameNotFoundException(username)
        val chat = if (messageDto.chatId == null && messageDto.receiverId != null) {
            createPrivateChat(sender.id!!, messageDto.receiverId)
        } else {
            chatRepository.findByIdAndDeletedFalse(messageDto.chatId!!)
                ?: throw ChatNotFoundException(messageDto.chatId)
        }
        val replyMessage = messageDto.replyToId?.let { messageRepository.findByIdOrNull(it) }
        println("Chatid = ${chat.id}")
        println("SenderUsername = ${sender.username}")

        val message = messageRepository.save(
            Message(
                chat = chat,
                sender = sender,
                messageType = messageType(messageDto),
                content = messageDto.content,
                hash = messageDto.hash,
                latitude = messageDto.latitude,
                longitude = messageDto.longitude,
                replyTo = replyMessage
            )
        )
        chat.lastMessage = message
        chatRepository.save(chat)

        val members = chatMemberRepository.findByChatIdAndDeletedFalse(chat.id!!)
        members.filter { it.user.id != sender.id }.forEach { member ->
            messageStatusRepository.save(
                MessageStatus(
                    message = message,
                    user = member.user,
                    isRead = false,
                    readAt = null
                )
            )
        }

        val response = MessageResponseDto.toResponse(message)
        if (chat.chatType == ChatType.GROUP) {
            println("Guruppaga xabar keldi = ${chat.id}")
            simpleMessagingTemplate.convertAndSend("/topic/chat.${chat.id}", response)
            members.filter { it.user.id != sender.id }.forEach { member ->
                println("Guruppadagi har bir userga yuborsih = ${member.user.username}")
                val unreadCount = messageStatusRepository.countUnreadMessages(member.user.id!!, chat.id!!)
                simpleMessagingTemplate.convertAndSendToUser(
                    member.user.username,
                    "/queue/chat-list",
                    ChatListItemDto.from(chat, message, unreadCount)
                )
                println("Har bir userga yuborlidi")
            }
        } else {
            members.forEach { member ->
                println("Qabul qiluvchi ${member.user.username}")
                simpleMessagingTemplate.convertAndSendToUser(
                    member.user.username,
                    "/queue/messages",
                    response
                )
                val unreadCount = messageStatusRepository.countUnreadMessages(member.user.id!!, chat.id!!)
                println("Har bir userga yuborilayabdi")
                println("Chat = ${chat.id} + MessageContent = ${message.content} + UnreadCount = $unreadCount")
                simpleMessagingTemplate.convertAndSendToUser(
                    member.user.username,
                    "/queue/chat-list",
                    ChatListItemDto.from(chat, message, unreadCount)
                )
            }
        }
    }

    private fun messageType(messageDto: MessageRequestDto): MessageType {
        return when {
            messageDto.hash != null -> {
                val file = fileRepository.findByHashAndDeletedFalse(messageDto.hash)
                    ?: throw FileHashNotFoundException(messageDto.hash)
                file.messageType
            }
            !messageDto.content.isNullOrBlank() -> {
                MessageType.TEXT
            }
            else -> {
                MessageType.OTHER
            }
        }
    }


    @Transactional
    override fun createPublicChat(requestDto: CreatePublicChatRequestDto): GetOneChatResponseDto {
        val currentUserId = getCurrentUserId()
        val currentUser =
            userRepository.findByIdAndDeletedFalse(currentUserId) ?: throw UserNotFoundException(currentUserId)
        var avatarUrl: String? = null
        val file = requestDto.file
        if (file != null) {
            val extension = file.originalFilename?.substringAfterLast(".")?.lowercase()
            val newFile = "${UUID.randomUUID()}.${extension}"
            val uploadPath = Paths.get("uploads/avatars")
            val savedFile = uploadPath.resolve(newFile)
            Files.createDirectories(uploadPath)
            Files.copy(file.inputStream, savedFile, StandardCopyOption.REPLACE_EXISTING)
            avatarUrl = "/uploads/avatars/$newFile"
        }
        val chat = Chat(chatType = ChatType.GROUP, groupName = requestDto.groupName, avatarUrl = avatarUrl)
        chatRepository.save(chat)
        chatMemberRepository.save(ChatMember(chat = chat, user = currentUser, role = MemberRole.OWNER))
        addMembers(chat.id!!, requestDto.membersRequestDto)
        return GetOneChatResponseDto.toResponse(chat)
    }

    override fun addMembers(chatId: Long, requestDto: AddMembersRequestDto?) {
        val currentUserId = getCurrentUserId()
        val chat = chatRepository.findByIdAndDeletedFalse(chatId) ?: throw ChatNotFoundException(chatId)
        val currentMember = chatMemberRepository.findByChatIdAndUserId(chatId, currentUserId)

        if (currentMember?.role != MemberRole.OWNER) {
            throw ChatAccessDeniedException(currentMember?.role?.name ?: " ")
        }
        requestDto?.members?.forEach { dto ->
            val user = userRepository.findByIdAndDeletedFalse(dto) ?: throw UserNotFoundException(dto)
            if (!chatMemberRepository.existsByChatIdAndUserId(chatId, user.id!!)) {
                chatMemberRepository.save(ChatMember(chat = chat, user = user, role = MemberRole.MEMBER))
            }
        }
    }

    override fun markMessagesAsRead(dto: ReadMessageRequestDto, username: String) {
        val user = userRepository.findByUsernameAndDeletedFalse(username) ?: throw UsernameNotFoundException(username)
        if (dto.messageIds.isEmpty()) return
        messageStatusRepository.markAsReadMessage(
            userId = user.id!!,
            chatId = dto.chatId,
            messageIds = dto.messageIds,
            readAt = Date()
        )
        val unreadCount = messageStatusRepository.countUnreadMessages(user.id!!, dto.chatId)
        simpleMessagingTemplate.convertAndSendToUser(
            username,
            "/queue/unread",
            UnreadCountDto(dto.chatId, unreadCount)
        )
    }

    override fun getMessage(chatId: Long, lastMessageId: Long?, pageable: Pageable): Page<MessageResponseDto> {
        val messages = messageRepository.getAllMessages(chatId, lastMessageId, pageable)
        return messages.map { MessageResponseDto.toResponse(it) }
    }

    override fun editMessage(
        chatId: Long,
        messageId: Long,
        newContent: String?,
        username: String
    ) {
        val chat = chatRepository.findByIdAndDeletedFalse(chatId) ?: throw ChatNotFoundException(chatId)
        val message = messageRepository.findByIdAndDeletedFalse(messageId) ?: throw MessageNotFoundException(messageId)
        if (message.chat.id != chat.id) {
            throw MessageChatMismatchException(message.content)
        }
        if (message.sender.username != username) {
            throw MessageAccessDeniedException(message.sender.username)
        }
        message.content = newContent
        message.edited = true
        val updateMessage = messageRepository.save(message)
        val response = MessageResponseDto.toResponse(updateMessage)
        val responseDto = ChatUpdateResponseDto(MessageEventType.UPDATED, response)
        val members = chatMemberRepository.findByChatIdAndDeletedFalse(chat.id!!)
        members.filter { it.user.username != username }.forEach { member ->
            println("Har bir userga update = ${member.user.username}")
            simpleMessagingTemplate.convertAndSendToUser(
                member.user.username,
                "/queue/messages",
                responseDto
            )
            println("Update bo'ldi")
        }
    }

    override fun deleteChat(requestDto: DeleteChatRequestDto, username: String) {
        val chat = chatRepository.findByIdAndDeletedFalse(requestDto.chatId) ?: throw ChatNotFoundException(requestDto.chatId)
        val currentUser = userRepository.findByUsernameAndDeletedFalse(username) ?: throw UsernameNotFoundException(username)
        val members = chatMemberRepository.findAllByChatIdAndDeletedFalse(requestDto.chatId)

        if (requestDto.deleted) {
            println("Private chat")
            if (chat.chatType == ChatType.PRIVATE) {
                chatRepository.trash(chat.id!!)
                members.forEach { member ->
                    member.deletedAt = LocalDateTime.now()
                    chatMemberRepository.trash(member.id!!)
                    println("chatlar o'chirildi")
                    simpleMessagingTemplate.convertAndSendToUser(
                        member.user.username,
                        "/queue/chat-delete",
                        chat.id!!
                    )
                }
            } else if (chat.chatType == ChatType.GROUP) {
                val currentMember = members.firstOrNull { it.user.id == currentUser.id }
                    ?: throw ChatNotFoundException(requestDto.chatId)
                if (currentMember.role != MemberRole.OWNER && currentMember.role != MemberRole.ADMIN) {
                    throw ChatNotDeletedPermissionException(currentUser.firstName)
                }
                chatRepository.trash(chat.id!!)
                members.forEach { member ->
                    member.deletedAt = LocalDateTime.now()
                    chatMemberRepository.trash(member.id!!)
                    simpleMessagingTemplate.convertAndSendToUser(
                        member.user.username,
                        "/queue/chat-delete",
                        chat.id!!
                    )
                }
            }
        } else {
            println("Faqat o'zidan chatni o'chirish")
            val currentMember = members.firstOrNull { it.user.id == currentUser.id }
                ?: throw ChatNotFoundException(requestDto.chatId)
            currentMember.deletedAt = LocalDateTime.now()
            chatMemberRepository.trash(currentMember.id!!)
            println("Faqat o'zidan o'chirildi")
        }

    }

    override fun getChatList(pageable: Pageable): Page<ChatListResponseDto> {
        val userId = getCurrentUserId()
        val chatMembers = chatMemberRepository.findByUserId(userId, pageable)
        return chatMembers.map { member ->
            val chat = member.chat
            val unreadMessages = messageStatusRepository.countUnreadMessages(userId, chat.id!!)
            val otherUser = if (chat.chatType == ChatType.PRIVATE) {
                chat.members.firstOrNull { it.user.id != userId }?.user
            } else null
            ChatListResponseDto.from(chat, chat.lastMessage, unreadMessages, otherUser)
        }
    }

    override fun getChatType(chatType: ChatType,pageable: Pageable): Page<ChatListResponseDto> {
        val currentUserId = getCurrentUserId()
        val chatMembers = chatMemberRepository.findByUserIdAndChatType(currentUserId, chatType, pageable)
        return chatMembers.map { member ->
            val chat = member.chat
            val unreadMessages = messageStatusRepository.countUnreadMessages(currentUserId, chat.id!!)
          val otherUser = if (chat.chatType == ChatType.PRIVATE){
                 chat.members.firstOrNull { member -> member.user.id != currentUserId }?.user
            }else null
            ChatListResponseDto.from(chat, chat.lastMessage, unreadMessages, otherUser)
        }
    }

    @Transactional
    override fun deleteMessage(requestDto: DeleteMessageRequestDto, username: String) {
        val chat = chatRepository.findByIdAndDeletedFalse(requestDto.chatId) ?: throw ChatNotFoundException(requestDto.chatId)
        val user = userRepository.findByUsernameAndDeletedFalse(username) ?: throw UsernameNotFoundException(username)
        val messages = messageRepository.findAllById(requestDto.messageIds)
        val members = chatMemberRepository.findByChatIdAndDeletedFalse(chat.id!!)
        messages.forEach { message ->
            if (message.chat.id != chat.id) {
                throw MessageChatMismatchException(message.content)
            }
            if (message.sender.id != user.id) {
                throw MessageAccessDeniedException(message.sender.username)
            }
        }
        messageRepository.trashList(requestDto.messageIds)
        val responseDto = ChatDeleteResponseDto(MessageEventType.DELETED, requestDto.messageIds)

        if (chat.chatType == ChatType.GROUP) {
            simpleMessagingTemplate.convertAndSend(
                "/topic/chat.${chat.id}",
                responseDto
            )
        } else {
            members.forEach { member ->
                simpleMessagingTemplate.convertAndSendToUser(
                    member.user.username,
                    "/queue/messages",
                    requestDto
                )
            }
        }
    }

    override fun getGroupChatDetails(chatId: Long): GroupChatResponseDto {
        val chat = chatRepository.findByIdAndDeletedFalse(chatId) ?: throw ChatNotFoundException(chatId)
        val members = chatMemberRepository.findByChatIdAndDeletedFalse(chatId)
        val memberDto = members.map { member ->
            MemberDto(member.user.id!!, member.user.firstName, member.user.avatarUrl)
        }
        return GroupChatResponseDto(chat.id!!, chat.groupName, chat.avatarUrl, memberDto)
    }

    fun saveFile(file: MultipartFile): FileResponseDto {
        val currentUserId = getCurrentUserId()
        val user = userRepository.findByIdAndDeletedFalse(currentUserId) ?: throw UserNotFoundException(currentUserId)
        val extension = file.originalFilename?.substringAfterLast(".")?.lowercase()
        val newFile = "${UUID.randomUUID()}.$extension"
        val uploadPath = Paths.get("uploads/files")
        val savedFile = uploadPath.resolve(newFile)
        Files.createDirectories(uploadPath)
        Files.copy(file.inputStream, savedFile, StandardCopyOption.REPLACE_EXISTING)

        val entity = FileEntity(
            user = user,
            originalName = file.originalFilename ?: newFile,
            fileUrl = "/uploads/files/$newFile",
            size = file.size,
            extension = extension,
            messageType = detectFileType(extension),
            hash = null
        )
        fileRepository.save(entity)

        val hashId = hashId(entity.id!!)
        entity.hash = hashId
        fileRepository.save(entity)
        return FileResponseDto(entity.id!!,entity.originalName,entity.hash!!,entity.messageType)
    }

    private fun hashId(id: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(id.toString().toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes)
    }

    private fun detectFileType(extension: String?): MessageType {
        return when (extension) {
            "jpg", "jpeg", "png", "gif" -> MessageType.IMAGE
            "mp4", "mov", "avi" -> MessageType.VIDEO
            "mp3", "wav" -> MessageType.AUDIO
            "pdf", "doc", "docx" -> MessageType.DOCUMENT
            else -> MessageType.OTHER
        }
    }

}

@Component
class PresenceEventListener(
    private val userRepository: UserRepository
) : ApplicationListener<AbstractSubProtocolEvent> {

    override fun onApplicationEvent(event: AbstractSubProtocolEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val username = accessor.user?.name ?: return
        val user = userRepository.findByUsernameAndDeletedFalse(username) ?: throw UsernameNotFoundException(username)
        when (event) {
            is SessionConnectEvent -> {
                user.status = UserStatus.ONLINE
                userRepository.save(user)
                println("🔵 $username online bo‘ldi")
            }

            is SessionDisconnectEvent -> {
                user.status = UserStatus.OFFLINE
                user.lastSeen = LocalDateTime.now()
                userRepository.save(user)
                println("⚪ $username offline bo‘ldi")
            }
        }
    }

}
