package uz.zero_one.chat

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.Locale

@RestControllerAdvice
class ExceptionHandlers(
    private val errorMessageSource: ResourceBundleMessageSource
) {
    @ExceptionHandler(DemoException::class)
    fun handleException(exception: DemoException): ResponseEntity<*> {
        return ResponseEntity.badRequest().body(exception.getErrorMessage(errorMessageSource))
    }
}

sealed class DemoException(message: String? = null) : RuntimeException(message) {
    abstract fun errorType(): ErrorCode

    protected open fun getErrorMessageArguments(): Array<Any?>? = null
    fun getErrorMessage(errorMessageSource: ResourceBundleMessageSource): BaseMessage {
        return BaseMessage(
            errorType().code,
            errorMessageSource.getMessage(
                errorType().toString(),
                getErrorMessageArguments(),
                Locale(LocaleContextHolder.getLocale().language)
            )
        )
    }
}

class TelegramIdNotFoundException(val id: Long): DemoException(){
    override fun errorType() = ErrorCode.TELEGRAM_ID_NOT_FOUND_EXCEPTION
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(id)
}

class InvalidTelegramDataException(val mess: String) : DemoException() {
    override fun errorType() = ErrorCode.INVALID_TELEGRAM_DATA_EXCEPTION
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(mess)
}

class UserNotFoundException(val id: Long) : DemoException(){
    override fun errorType() = ErrorCode.USER_NOT_FOUND_EXCEPTION
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(id)
}

class ChatNotFoundException(val id: Long) : DemoException(){
    override fun errorType() = ErrorCode.CHAT_NOT_FOUND_EXCEPTION
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(id)
}

class ChatAccessDeniedException(val msg: String) : DemoException(){
    override fun errorType() = ErrorCode.CHAT_ACCESS_DENIED
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(msg)
}

class ChatMemberNotFoundException(val msg: String) : DemoException(){
    override fun errorType() = ErrorCode.CHAT_MEMBER_NOT_FOUND
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(msg)
}

class UsernameNotFoundException(val msg: String) : DemoException(){
    override fun errorType() = ErrorCode.USERNAME_NOT_FOUND_EXCEPTION
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(msg)
}

class MessageNotFoundException(val id: Long) : DemoException(){
    override fun errorType() = ErrorCode.MESSAGE_NOT_FOUND_EXCEPTION
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(id)
}