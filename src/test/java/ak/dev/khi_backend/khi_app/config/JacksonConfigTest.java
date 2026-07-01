package ak.dev.khi_backend.khi_app.config;

import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionDTO;
import ak.dev.khi_backend.khi_app.dto.publishment.sound.SoundTrackDtos;
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

    @Test
    void bindsNestedSoundFileIdFromResponseShapedUpdatePayload() throws Exception {
        SoundTrackDtos.UpdateRequest request = objectMapper.readValue(
                """
                {
                  "files": [
                    {
                      "id": 17,
                      "fileUrl": "https://cdn.example.com/original.mp3",
                      "durationMinutes": 3.0
                    }
                  ]
                }
                """,
                SoundTrackDtos.UpdateRequest.class
        );

        assertEquals(17L, request.getFiles().getFirst().getId());
    }
}
