package ak.dev.khi_backend.khi_app.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Configuration
public class I18nConfig implements WebMvcConfigurer {

    public static final Locale LOCALE_EN = Locale.ENGLISH;
    public static final Locale LOCALE_CKB = Locale.forLanguageTag("ckb"); // Sorani (Central Kurdish)
    public static final Locale LOCALE_KMR = Locale.forLanguageTag("kmr"); // Kurmanji (Northern Kurdish)

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();

        // ✅ Will load:
        // classpath:i18n/messages_en.properties
        // classpath:i18n/messages_ckb.properties
        // classpath:i18n/messages_kmr.properties
        ms.setBasename("classpath:i18n/messages");
        ms.setDefaultEncoding(StandardCharsets.UTF_8.name());

        // If message key is missing, return the key (better than crashing)
        ms.setUseCodeAsDefaultMessage(true);

        // Optional: cache messages for dev changes (set higher in production)
        ms.setCacheSeconds(10);

        return ms;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();

        resolver.setSupportedLocales(List.of(
                LOCALE_EN,
                LOCALE_CKB,
                LOCALE_KMR
        ));

        // Default language if client sends no Accept-Language
        resolver.setDefaultLocale(LOCALE_EN);

        return resolver;
    }

    /**
     * Optional ✅:
     * allows changing language by query param:
     * /api/projects?lang=ckb
     * /api/projects?lang=kmr
     * /api/projects?lang=en
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        lci.setParamName("lang");
        return lci;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
