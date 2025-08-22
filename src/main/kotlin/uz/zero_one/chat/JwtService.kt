package uz.zero_one.chat

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.access.token.secretKey}")
    private val jwtAccessTokenSecretKey: String,

    @Value("\${jwt.access.token.expire.date}")
    private val jwtAccessTokenExpireDate: Long,

    @Value("\${jwt.refresh.token.secretKey}")
    private val jwtRefreshTokenSecretKey: String,

    @Value("\${jwt.refresh.token.expire.date}")
    private val jwtRefreshTokenExpireDate: Long
) {

    fun generateAccessToken(user: User): String {
        val date = Date()
        return Jwts.builder()
            .subject(user.username)
            .issuedAt(date)
            .expiration(Date(date.time + jwtAccessTokenExpireDate))
            .signWith(getAccessTokenSecretKey())
            .claim("userId",user.id)
            .compact()
    }

    fun generateRefreshToken(user: User): String {
        val date = Date()
        return Jwts.builder()
            .subject(user.username)
            .issuedAt(date)
            .expiration(Date(date.time + jwtRefreshTokenExpireDate))
            .signWith(getRefreshTokenSecretKey())
            .compact()
    }

    private fun getAccessTokenSecretKey(): SecretKey {
        return Keys.hmacShaKeyFor(jwtAccessTokenSecretKey.toByteArray())
    }

    private fun getRefreshTokenSecretKey(): SecretKey {
        return Keys.hmacShaKeyFor(jwtRefreshTokenSecretKey.toByteArray())
    }

    fun validateAccessToken(accessToken: String?) {
        try {
            val key = getAccessTokenSecretKey()
            Jwts.parser().verifyWith(key).build().parseSignedClaims(accessToken);
        } catch (e: SecurityException) {
            throw AuthenticationCredentialsNotFoundException("JWT was expired or incorrect")
        } catch (e: MalformedJwtException) {
            throw AuthenticationCredentialsNotFoundException("JWT was expired or incorrect")
        } catch (e: ExpiredJwtException) {
            throw AuthenticationCredentialsNotFoundException("Expired JWT token.")
        } catch (e: UnsupportedJwtException) {
            throw AuthenticationCredentialsNotFoundException("Unsupported JWT token.")
        } catch (e: IllegalArgumentException) {
            throw AuthenticationCredentialsNotFoundException("JWT token compact of handler are invalid.")
        }
    }

    fun validateRefreshToken(refreshToken: String?) {
        try {
            val key = getRefreshTokenSecretKey()
            Jwts.parser().verifyWith(key).build().parseSignedClaims(refreshToken)
        } catch (e: SecurityException) {
            throw AuthenticationCredentialsNotFoundException("JWT was expired or incorrect")
        } catch (e: MalformedJwtException) {
            throw AuthenticationCredentialsNotFoundException("JWT was expired or incorrect")
        } catch (e: ExpiredJwtException) {
            throw AuthenticationCredentialsNotFoundException("Expired JWT token.")
        } catch (e: UnsupportedJwtException) {
            throw AuthenticationCredentialsNotFoundException("Unsupported JWT token.")
        } catch (e: IllegalArgumentException) {
            throw AuthenticationCredentialsNotFoundException("JWT token compact of handler are invalid.")
        }
    }

    fun accessTokenClaims(accessToken: String?): Claims {
        return Jwts.parser().verifyWith(getAccessTokenSecretKey()).build().parseSignedClaims(accessToken).payload
    }

    fun refreshTokenClaims(refreshToken: String?): Claims {
        return Jwts.parser().verifyWith(getRefreshTokenSecretKey()).build().parseSignedClaims(refreshToken).payload
    }
}