package uz.zero_one.chat

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.server.ServerHttpRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.support.DefaultHandshakeHandler
import java.security.Principal
import java.util.UUID
import kotlin.text.startsWith
import kotlin.text.substring

@Component
class JwtAuthenticationFilter(private val jwtService: JwtService, ): OncePerRequestFilter(){

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authorization = request.getHeader("Authorization")
        if (authorization == null || !authorization.startsWith("Bearer ")){
            filterChain.doFilter(request,response)
            return
        }
        val token = authorization.substring(7)
        println("Token = ${token}")
        jwtService.validateAccessToken(token)
        val claims = jwtService.accessTokenClaims(token)
        val username = claims.subject
        val userId = (claims["userId"] as Int).toLong()
        val authorities = mutableSetOf<GrantedAuthority>()
        val auth = UsernamePasswordAuthenticationToken(username, token, authorities)
        auth.details = userId
        SecurityContextHolder.getContext().authentication = auth
        filterChain.doFilter(request, response)
    }

}

@Component
class CustomHandshakeHandler : DefaultHandshakeHandler() {
    override fun determineUser(
        request: ServerHttpRequest,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Principal {
        val authentication = SecurityContextHolder.getContext().authentication
        val username = authentication?.name ?: "anon-${UUID.randomUUID()}"
        return StompPrincipal(username)
    }
}

class StompPrincipal(private val name: String) : Principal {
    override fun getName(): String = name
}
