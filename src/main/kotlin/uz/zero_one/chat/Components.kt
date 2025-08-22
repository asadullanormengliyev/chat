package uz.zero_one.chat

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.Locale

@Configuration
class WebMvcConfigure : WebMvcConfigurer{

    @Bean
    fun errorMessageSource() = ResourceBundleMessageSource().apply {
        setDefaultEncoding(Charsets.UTF_8.name())
        setDefaultLocale(Locale("uz"))
        setBasename("i18n/errors")
        setFallbackToSystemLocale(false)
    }
}
