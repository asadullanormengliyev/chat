package uz.zero_one.chat

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class TelegramAuthValidator(
    @Value("\${telegram.bot.token}") private val botToken: String
) {

    fun validate(request: TelegramLoginRequestDto): Boolean {
        val secretKey = MessageDigest.getInstance("SHA-256").digest(botToken.toByteArray())
        val fields = mutableMapOf(
            "auth_date" to request.authDate.toString(),
            "id" to request.telegramId.toString()
        )
        request.firstName?.let { fields["first_name"] = it }
        request.username?.let { fields["username"] = it }
        request.photoUrl?.let { fields["photo_url"] = it }

        val dataCheckString = fields
            .toSortedMap()
            .map { "${it.key}=${it.value}" }
            .joinToString("\n")

        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(secretKey, "HmacSHA256"))
        val hashBytes = hmac.doFinal(dataCheckString.toByteArray())
        val hexHash = hashBytes.joinToString("") { "%02x".format(it) }

        return hexHash == request.hash
    }

}

fun getCurrentUserId(): Long {
    val auth = SecurityContextHolder.getContext().authentication
    return auth.details as Long
}

