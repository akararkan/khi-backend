package ak.dev.khi_backend.khi_app.config;

import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JacksonConfigTest {

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    @Test
    void ignoresUnknownIdInUpdatePayload() throws Exception {
        ImageCollectionDTO.UpdateRequest request = objectMapper.readValue(
                """
                {
                  "id": 8,
                  "slugCkb": "example"
                }
                """,
                ImageCollectionDTO.UpdateRequest.class
        );

        assertEquals("example", request.getSlugCkb());
    }

    @Test
    void stillRejectsOtherUnknownProperties() {
        assertThrows(
                UnrecognizedPropertyException.class,
                () -> objectMapper.readValue(
                        """
                        {
                          "slugCkb": "example",
                          "unknownTypo": true
                        }
                        """,
                        ImageCollectionDTO.UpdateRequest.class
                )
        );
    }
}
