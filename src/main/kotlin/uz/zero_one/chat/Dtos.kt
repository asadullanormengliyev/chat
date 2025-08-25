package uz.zero_one.chat

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.time.ZoneId
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
    val bio: String?
)

data class GetOneUserResponseDto(
    val id: Long,
    val telegramId: Long,
    val firstName: String,
    val username: String,
    val bio: String?
){
    companion object{
        fun toResponse(user: User): GetOneUserResponseDto{
            return GetOneUserResponseDto(
                id = user.id!!,
                telegramId = user.telegramId,
                firstName = user.firstName,
                username = user.username,
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
    val chatId: Long,
    val messageType: MessageType,
    val content: String? = null,
    val fileUrl: String? = null,
    val fileHash: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val replyToId: Long? = null
)

data class MessageResponseDto(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val messageType: MessageType,
    val content: String?,
    val fileUrl: String?,
    val fileHash: String?,
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
                fileUrl = message.fileUrl,
                fileHash = message.fileHash,
                latitude = message.latitude,
                longitude = message.longitude,
                replyToId = message.replyTo?.id,
                createdAt = message.createdDate
            )
    }
}

data class AddMembersRequestDto(
    val ids: List<Long>
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

data class FileUploadResponseDto(
    val fileUrl: String,
    val fileHash: String
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

data class ChatListItemDto(
    val chatId: Long,
    val chatName: String?,
    val chatType: ChatType,
    val lastMessage: String?,
    val lastMessageAt: LocalDateTime,
    val unreadCount: Long
) {
    companion object {
        fun from(chat: Chat, message: Message, unreadCount: Long): ChatListItemDto {
            return ChatListItemDto(
                chatId = chat.id!!,
                chatName = chat.groupName ?: message.sender.firstName,
                chatType = chat.chatType,
                lastMessage = message.content,
                lastMessageAt = message.createdDate!!.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime(),
                unreadCount = unreadCount
            )
        }
    }
}
