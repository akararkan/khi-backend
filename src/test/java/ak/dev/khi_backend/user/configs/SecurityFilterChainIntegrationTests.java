package ak.dev.khi_backend.user.configs;

import ak.dev.khi_backend.khi_app.dto.media.MediaDtos.UploadResponse;
import ak.dev.khi_backend.khi_app.service.media.MediaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFilterChainIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaService mediaService;

    @Test
    void contactPageWritesAreRestrictedToAdmins() throws Exception {
        mockMvc.perform(post("/api/v1/contact")
                        .with(user("employee").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/contact/1")
                        .with(user("employee").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/contact/1")
                        .with(user("employee").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/contact")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mediaUploadsAreRestrictedToAdmins() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1});
        when(mediaService.upload(any(), anyString())).thenReturn(
                UploadResponse.builder()
                        .fileUrl("https://cdn.example.com/image.png")
                        .fileName("image.png")
                        .fileSize(1L)
                        .contentType(MediaType.IMAGE_PNG_VALUE)
                        .build());

        mockMvc.perform(multipart("/api/v1/media/upload")
                        .file(file)
                        .with(user("employee").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/media/upload")
                        .file(file)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
