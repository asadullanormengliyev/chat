package uz.zero_one.chat

import jakarta.transaction.Transactional
import org.springframework.context.ApplicationListener
import org.springframework.data.domain.Page
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
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date

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
    fun sendMessage(messageDto: MessageRequestDto,username: String)
    fun createPublicChat(groupName: String,file: MultipartFile?): GetOneChatResponseDto
    fun addMembers(chatId: Long,requestDto: AddMembersRequestDto)
    fun saveFile(file: MultipartFile): FileUploadResponseDto
    fun markMessagesAsRead(dto: ReadMessageRequestDto, username: String)
    fun getAllMessage(chatId: Long,pageable: Pageable): Page<MessageResponseDto>
    fun editMessage(chatId: Long, messageId: Long, newContent: String?, username: String)
    fun getChatList(): List<ChatListItemDto>
    fun deletedChatForMe(chatId: Long)
    fun deletedChatForEveryone(chatId: Long)
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
                    avatarUrl = request.photoUrl,
                    authDate = request.authDate,
                    avatarHash = null,
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

        if (file != null && !file.isEmpty) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
            val extension = file.originalFilename?.substringAfterLast(".")?.lowercase()
            val newFileName = "${timestamp}.${extension}"
            val uploadDir = "uploads/avatars"
            val dest = File(uploadDir, newFileName)
            dest.parentFile.mkdirs()
            file.transferTo(dest)

            val bytes = Files.readAllBytes(dest.toPath())
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
            user.avatarUrl = dest.path
            user.avatarHash = hash
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
        return UserStatusDto(user.status,user.lastSeen)
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
        println("getAuthenticationFromToken!!!!!")
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
    private val messageStatusRepository: MessageStatusRepository
) : ChatService {

    @Transactional
    override fun createPrivateChat(senderId: Long, receiverId: Long): Chat {
        val existing = chatRepository.findPrivateChat(receiverId, senderId)
        if (existing != null) return existing
        val sender = userRepository.findByIdAndDeletedFalse(senderId) ?: throw UserNotFoundException(senderId)
        val user = userRepository.findByIdAndDeletedFalse(receiverId)
            ?: throw UserNotFoundException(receiverId)

        val chat = chatRepository.save(
            Chat(chatType = ChatType.PRIVATE,null,null,null)
        )
        chatMemberRepository.save(ChatMember(chat = chat, user = sender))
        chatMemberRepository.save(ChatMember(chat = chat, user = user))
        return chat
    }

    @Transactional
    override fun sendMessage(messageDto: MessageRequestDto, username: String) {
         val sender = userRepository.findByUsernameAndDeletedFalse(username) ?: throw UsernameNotFoundException(username)
        val chat = if (messageDto.chatId == null && messageDto.receiverId != null) {
            createPrivateChat(sender.id!!, messageDto.receiverId)
        } else {
            chatRepository.findByIdAndDeletedFalse(messageDto.chatId!!) ?: throw ChatNotFoundException(messageDto.chatId)
        }
        val replyMessage = messageDto.replyToId?.let { messageRepository.findByIdOrNull(it) }
        println("Chatid = ${chat.id}")
        println("SenderUsername = ${sender.username}")
        val message = messageRepository.save(
            Message(
                chat = chat,
                sender = sender,
                messageType = messageDto.messageType,
                content = messageDto.content,
                fileUrl = messageDto.fileUrl,
                fileHash = messageDto.fileHash,
                latitude = messageDto.latitude,
                longitude = messageDto.longitude,
                replyTo = replyMessage
            )
        )

        val members = chatMemberRepository.findByChatIdAndDeletedFalse(chat.id!!)
        val now = LocalDateTime.now()
        members.forEach { member ->
            member.lastMessageAt = now
            chatMemberRepository.save(member)
        }
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
        if (chat.chatType == ChatType.GROUP){
            simpleMessagingTemplate.convertAndSend("/topic/chat.${chat.id}", response)
            members.filter { it.user.id != sender.id }.forEach { member ->
                val unreadCount = messageStatusRepository.countUnreadMessages(member.user.id!!, chat.id!!)
                simpleMessagingTemplate.convertAndSendToUser(
                    member.user.username,
                    "/queue/chat-list",
                    ChatListItemDto.from(chat, message, unreadCount)
                )
            }
        }else{
            members.forEach { member ->
                if (member.user.id != sender.id) {
                    println("Qabul qiluvchi ${member.user.username}")
                    simpleMessagingTemplate.convertAndSendToUser(
                        member.user.username,
                        "/queue/messages",
                        response
                    )
                    val unreadCount = messageStatusRepository.countUnreadMessages(member.user.id!!,chat.id!!)
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
    }

    @Transactional
    override fun createPublicChat(groupName: String,file: MultipartFile?): GetOneChatResponseDto {
        val currentUserId = getCurrentUserId()
        val currentUser = userRepository.findByIdAndDeletedFalse(currentUserId) ?: throw UserNotFoundException(currentUserId)

        var avatarUrl: String? = null
        var avatarHash: String? = null
        if (file != null) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
            val extension = file.originalFilename?.substringAfterLast(".")?.lowercase()
            val newFileName = "${timestamp}.${extension}"
            val uploadPath = Paths.get("uploads/avatars")
            val savedFile = uploadPath.resolve(newFileName)
            Files.createDirectories(uploadPath)
            Files.copy(file.inputStream, savedFile, StandardCopyOption.REPLACE_EXISTING)
            val bytes = Files.readAllBytes(savedFile)
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
            avatarUrl ="/uploads/avatars/$newFileName"
            avatarHash = hash
        }
        val chat = Chat(chatType = ChatType.GROUP, groupName = groupName, avatarUrl = avatarUrl, avatarHash = avatarHash)
        chatRepository.save(chat)
        chatMemberRepository.save(ChatMember(chat = chat, user = currentUser, role = MemberRole.OWNER))
        return GetOneChatResponseDto.toResponse(chat)
    }

    override fun addMembers(chatId: Long, requestDto: AddMembersRequestDto) {
        val currentUserId = getCurrentUserId()
        val chat = chatRepository.findByIdAndDeletedFalse(chatId) ?: throw ChatNotFoundException(chatId)
        val currentMember = chatMemberRepository.findByChatIdAndUserId(chatId, currentUserId)

        if (currentMember?.role != MemberRole.OWNER) {
            throw ChatAccessDeniedException(currentMember?.role?.name ?: " ")
        }
        requestDto.ids.forEach { dto ->
            val user = userRepository.findByIdAndDeletedFalse(dto) ?: throw UserNotFoundException(dto)
            if (!chatMemberRepository.existsByChatIdAndUserId(chatId, user.id!!)) {
                chatMemberRepository.save(ChatMember(chat = chat, user = user, role = MemberRole.MEMBER))
            }
        }
    }

    override fun saveFile(file: MultipartFile): FileUploadResponseDto {
        var fileUrl: String
        var fileHash: String
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        val extension = file.originalFilename?.substringAfterLast(".")?.lowercase()
        val newFileName = "${timestamp}.${extension}"
        val uploadPath = Paths.get("uploads/avatars")
        val savedFile = uploadPath.resolve(newFileName)
        Files.createDirectories(uploadPath)
        Files.copy(file.inputStream, savedFile,StandardCopyOption.REPLACE_EXISTING)
        val bytes = Files.readAllBytes(savedFile)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
        fileUrl ="/uploads/avatars/$newFileName"
        fileHash = hash
        return FileUploadResponseDto(fileUrl,fileHash)
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

    override fun getAllMessage(chatId: Long,pageable: Pageable): Page<MessageResponseDto> {
        val messages = messageRepository.getAllMessage(chatId,pageable)
        return messages.map { message -> MessageResponseDto.toResponse(message) }
    }

    override fun editMessage(
        chatId: Long,
        messageId: Long,
        newContent: String?,
        username: String
    ){
        val chat = chatRepository.findByIdAndDeletedFalse(chatId) ?: throw ChatNotFoundException(chatId)
        val message = messageRepository.findByIdAndDeletedFalse(messageId) ?: throw MessageNotFoundException(messageId)
        if (message.chat.id != chat.id){
            throw IllegalArgumentException("Message bu chatga tegshli emas")
        }
        if (message.sender.username != username) {
            throw AccessDeniedException("Siz faqat o‘z xabaringizni o‘zgartira olasiz!")
        }
        message.content = newContent
        message.edited = true
        val updateMessage = messageRepository.save(message)
        val response = MessageResponseDto.toResponse(updateMessage)

        val members = chatMemberRepository.findByChatIdAndDeletedFalse(chat.id!!)
        members.filter { it.user.username != username }.forEach { member ->
            simpleMessagingTemplate.convertAndSendToUser(
                member.user.username,
                "/queue/messages",
                response
            )
        }
    }

    override fun getChatList(): List<ChatListItemDto> {
        val userId = getCurrentUserId()
        val members = chatMemberRepository.findByUserIdAndDeletedFalse(userId)
        val chats = members.map { it.chat }.distinctBy { it.id }
        return chats.map { chat ->
            val lastMessage = messageRepository.findTopByChatIdOrderByCreatedDateDesc(chat.id!!)
            val unreadCount = messageStatusRepository.countUnreadMessages(userId, chat.id!!)
            val otherMember = chatMemberRepository.findByChatIdAndDeletedFalse(chat.id!!)
                .firstOrNull { it.user.id != userId }
            val (chatName, chatImageUrl) = if (chat.chatType == ChatType.GROUP) {
                chat.groupName to chat.avatarUrl
            } else {
                otherMember?.user?.firstName to otherMember?.user?.avatarUrl
            }
            ChatListItemDto.from(chat, lastMessage, unreadCount).copy(chatName = chatName, chatImageUrl = chatImageUrl)
        }.sortedByDescending { it.lastMessageAt }
    }

    override fun deletedChatForMe(chatId: Long) {
        val currentUserId = getCurrentUserId()
        val member = chatMemberRepository.findByChatIdAndUserId(chatId, currentUserId)?:throw ChatNotFoundException(chatId)
        member.deletedAt = LocalDateTime.now()
        chatMemberRepository.trash(member.id!!)
    }

    override fun deletedChatForEveryone(chatId: Long) {
        TODO("Not yet implemented")
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
