package uz.zero_one.chat

import com.fasterxml.jackson.annotation.JsonProperty
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
    val content: String?,
    val replyToId: Long? = null
)

data class MessageResponseDto(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val content: String?,
    val fileUrl: String?,
    val replyToId: Long?,
    val createdAt: Date?
) {
    companion object {
        fun fromEntity(message: Message): MessageResponseDto =
            MessageResponseDto(
                id = message.id!!,
                chatId = message.chat.id!!,
                senderId = message.sender.id!!,
                content = message.content,
                fileUrl = message.fileUrl,
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
