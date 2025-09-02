package uz.zero_one.chat

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.util.Date

data class BaseMessage(val code: Int,val message: String?)

data class TelegramLoginRequestDto(
    val telegramId: Long,
    val username: String?,
    @JsonProperty("first_name")
    val firstName: String?,
    @JsonProperty("photo_url")
    val photoUrl: String?,
    @JsonProperty("auth_date")
    val authDate: Long,
    val hash: String
)

data class JwtResponseDto(
    val accessToken: String,
    val refreshToken: String
)

data class UserUpdateRequestDto(
    val firstName: String?,
    val username: String?,
    val bio: String?,
    val file: MultipartFile?
)

data class GetOneUserResponseDto(
    val id: Long,
    val telegramId: Long,
    val firstName: String,
    val username: String,
    val avatarUrl: String?,
    val bio: String?
){
    companion object{
        fun toResponse(user: User): GetOneUserResponseDto{
            return GetOneUserResponseDto(
                id = user.id!!,
                telegramId = user.telegramId,
                firstName = user.firstName,
                username = user.username,
                avatarUrl = user.avatarUrl,
                bio = user.bio
            )
        }
    }
}

data class GetOneChatResponseDto(
    val id: Long,
    val chatType: ChatType,
    val groupName: String?,
    val avatarUrl: String?
){
    companion object{
        fun toResponse(chat: Chat): GetOneChatResponseDto{
            return GetOneChatResponseDto(
                id = chat.id!!,
                chatType = chat.chatType,
                groupName = chat.groupName,
                avatarUrl = chat.avatarUrl
            )
        }
    }
}

data class MessageRequestDto(
    val chatId: Long?,
    val receiverId: Long?,
    val messageType: MessageType,
    val content: String? = null,
    val hash: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val replyToId: Long? = null
)

data class MessageResponseDto(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val messageType: MessageType?,
    val content: String?,
    val hash: String?,
    val latitude: Double?,
    val longitude: Double?,
    val replyToId: Long?,
    val createdAt: Date?
) {
    companion object {
        fun toResponse(message: Message): MessageResponseDto =
            MessageResponseDto(
                id = message.id!!,
                chatId = message.chat.id!!,
                senderId = message.sender.id!!,
                messageType = message.messageType,
                content = message.content,
                hash = message.hash,
                latitude = message.latitude,
                longitude = message.longitude,
                replyToId = message.replyTo?.id,
                createdAt = message.createdDate
            )
    }
}

data class AddMembersRequestDto(
    val members: Set<Long>
)

data class ChatUserDto(
    val chatId: Long,
    val chatType: String,
    val groupName: String?,
    val members: List<UserDto>
)

data class UserDto(
    val id: Long,
    val firstName: String,
    val userName: String,
    val avatarUrl: String?
)

data class UnreadCountDto(
    val chatId: Long,
    val unreadCount: Long
)

data class ReadMessageRequestDto(
    val chatId: Long,
    val messageIds: List<Long>
)

data class UserStatusDto(
    val status: UserStatus,
    val lastSeen: LocalDateTime?
)


data class EditMessageRequestDto(
    val chatId: Long,
    val messageId: Long,
    val message: String?
)

data class DeleteMessageRequestDto(
    val chatId: Long,
    val messageIds: List<Long>
)

data class CreatePublicChatRequestDto(
    val groupName: String,
    val membersRequestDto: AddMembersRequestDto?,
    val file: MultipartFile?
)

data class FileResponseDto(
    val id: Long,
    val originalName: String,
    val hash: String,
    val messageType: MessageType
)

data class ChatListResponseDto(
    val chatId: Long,
    val chatName: String?,
    val chatType: ChatType,
    val chatImageUrl: String?,
    val lastMessage: MessageDto?,
    val lastMessageAt: Date?,
    val unreadCount: Long,
    val userId: Long? = null,
    val status: UserStatus? = null,
    val lastSeen: LocalDateTime? = null
){
    companion object{
        fun from(chat: Chat, lastMessage: Message?, unreadCount: Long, user: User?): ChatListResponseDto {
            return ChatListResponseDto(
                chatId = chat.id!!,
                chatName = if (chat.chatType == ChatType.GROUP) {
                    chat.groupName
                } else {
                    user?.firstName
                },
                chatType = chat.chatType,
                chatImageUrl = chat.avatarUrl ?: user?.avatarUrl,
                lastMessage = lastMessage?.let { MessageDto.toResponse(it) },
                lastMessageAt = lastMessage?.createdDate,
                unreadCount = unreadCount,
                userId = user?.id,
                status = user?.status,
                lastSeen = user?.lastSeen
            )
        }
    }
}

data class MessageDto(
    val id: Long,
    val chatId: Long,
    val messageType: MessageType?,
    val content: String?,
    val createdAt: Date?
){
    companion object{
        fun toResponse(message: Message): MessageDto{
            return MessageDto(
                id = message.id!!,
                chatId = message.chat.id!!,
                messageType = message.messageType,
                content = message.content,
                createdAt = message.createdDate
            )
        }
    }
}

data class GroupChatResponseDto(
    val id: Long,
    val groupName: String?,
    val avatarUrl: String?,
    val members: List<MemberDto>
)

data class MemberDto(
    val id: Long,
    val fullName: String,
    val avatarUrl: String?
)

data class DeleteChatRequestDto(
    val chatId: Long,
    val deleted: Boolean
)

data class ChatDeleteResponseDto(
    val type: MessageEventType,
    val messageIds: List<Long>
)

data class ChatUpdateResponseDto(
    val type: MessageEventType,
    val messageResponseDto: MessageResponseDto
)
