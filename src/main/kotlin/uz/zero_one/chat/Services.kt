package uz.zero_one.chat

import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date

interface UserService {
    fun login(request: TelegramLoginRequestDto): JwtResponseDto
    fun updateUser(id: Long, request: UserUpdateRequestDto, file: MultipartFile?)
    fun deleteUser(id: Long)
    fun getAllUsersByUsernameSearch(search: String?, pageable: Pageable): Page<GetOneUserResponseDto>
}

interface ChatService {
    fun createPrivateChat(userId: Long): GetOneChatResponseDto
    fun sendMessage(messageDto: MessageRequestDto, file: MultipartFile?)
    fun createPublicChat(groupName: String,file: MultipartFile?): GetOneChatResponseDto
    fun addMembers(chatId: Long,requestDto: AddMembersRequestDto)
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
        println("AccessToken = $accessToken")
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

}

@Service
class ChatServiceImpl(
    private val chatRepository: ChatRepository,
    private val chatMemberRepository: ChatMemberRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val simpleMessagingTemplate: SimpMessagingTemplate
) : ChatService {

    @Transactional
    override fun createPrivateChat(userId: Long): GetOneChatResponseDto {
        val currentUserId = getCurrentUserId()
        val user = userRepository.findByIdAndDeletedFalse(userId) ?: throw UserNotFoundException(userId)
        val currentUser =
            userRepository.findByIdAndDeletedFalse(currentUserId) ?: throw UserNotFoundException(currentUserId)

        val existing = chatRepository.findPrivateChat(user.id!!, currentUserId)
        if (existing != null) return GetOneChatResponseDto.toResponse(existing)

        val chat = chatRepository.save(
            Chat(
                chatType = ChatType.PRIVATE,
                groupName = null,
                avatarUrl = null,
                avatarHash = null
            )
        )

        chatMemberRepository.save(ChatMember(chat = chat, user = user))
        chatMemberRepository.save(ChatMember(chat = chat, user = currentUser))

        return GetOneChatResponseDto.toResponse(chat)
    }

    @Transactional
    override fun sendMessage(messageDto: MessageRequestDto, file: MultipartFile?) {
        val senderId = getCurrentUserId()
        val chat = chatRepository.findByIdAndDeletedFalse(messageDto.chatId) ?: throw ChatNotFoundException(messageDto.chatId)
        val sender = userRepository.findByIdAndDeletedFalse(senderId) ?: throw UserNotFoundException(
            senderId
        )
        println("ChatId = ${chat.id}")
        println("Yuboruvchi = ${sender.username}")
        var fileUrl: String? = null
        var fileHash: String? = null
        if (file != null) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
            val extension = file.originalFilename?.substringAfterLast(".")?.lowercase()
            val newFileName = "${timestamp}.${extension}"
            val uploadPath = Paths.get("uploads")
            val savedFile = uploadPath.resolve(newFileName)
            Files.createDirectories(uploadPath)
            Files.copy(
                file.inputStream,
                savedFile,
                StandardCopyOption.REPLACE_EXISTING
            )
            val bytes = Files.readAllBytes(savedFile)
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
            fileUrl ="/uploads/$newFileName"
            fileHash = hash
        }

        val message = messageRepository.save(
            Message(
                chat = chat,
                sender = sender,
                content = messageDto.content,
                fileUrl = fileUrl,
                fileHash = fileHash,
                replyTo = null
            )
        )

        val response = MessageResponseDto.fromEntity(message)
        println("Response = $response")
        val members = chatMemberRepository.findByChatIdAndDeletedFalse(chat.id!!)
        members.forEach { member -> println("Memeberlar = $member") }
        members.forEach { member ->
            println("memeber ${member.user.username}")
            if (member.user.id != sender.id) {
                println("Qabul qiluvchi ${member.user.username}")
                simpleMessagingTemplate.convertAndSendToUser(
                    member.user.username,
                    "/queue/messages",
                    response
                )
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
            Files.copy(
                file.inputStream,
                savedFile,
                StandardCopyOption.REPLACE_EXISTING
            )
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

        if (currentMember.role != MemberRole.OWNER) {
            throw ChatAccessDeniedException(currentMember.role.name)
        }
        requestDto.ids.forEach { dto ->
            val user = userRepository.findByIdAndDeletedFalse(dto) ?: throw UserNotFoundException(dto)
            if (!chatMemberRepository.existsByChatIdAndUserId(chatId, user.id!!)) {
                chatMemberRepository.save(ChatMember(chat = chat, user = user, role = MemberRole.MEMBER))
            }
        }
    }



}