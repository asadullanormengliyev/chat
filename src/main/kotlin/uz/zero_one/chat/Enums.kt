package uz.zero_one.chat

enum class ChatType {
    PRIVATE, GROUP
}

enum class MemberRole {
    OWNER, MEMBER,ADMIN
}

enum class ErrorCode(val code: Int){
    TELEGRAM_ID_NOT_FOUND_EXCEPTION(100),
    INVALID_TELEGRAM_DATA_EXCEPTION(101),
    USER_NOT_FOUND_EXCEPTION(102),
    CHAT_NOT_FOUND_EXCEPTION(103),
    CHAT_ACCESS_DENIED(104),
    CHAT_MEMBER_NOT_FOUND(105)
}

