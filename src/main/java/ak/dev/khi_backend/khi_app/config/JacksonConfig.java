package ak.dev.khi_backend.khi_app.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public boolean handleUnknownProperty(
                    DeserializationContext context,
                    JsonParser parser,
                    com.fasterxml.jackson.databind.JsonDeserializer<?> deserializer,
                    Object beanOrClass,
                    String propertyName
            ) throws IOException {
                if (!"id".equals(propertyName)) {
                    return false;
                }

                // Update IDs come from the URL. Tolerate response-shaped request payloads
                // that also contain an ID, but keep rejecting every other unknown field.
                parser.skipChildren();
                return true;
            }
        });
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
