package ak.dev.khi_backend.khi_app.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicApiContractIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicListContractsReturnExpectedEnvelopes() throws Exception {
        mockMvc.perform(get("/api/v1/about").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/v1/contact/active").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/v1/services/all").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void featuredAndSitemapArePublic() throws Exception {
        mockMvc.perform(get("/featured").param("locale", "ckb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/v1/sitemap").param("locale", "ku"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locale").value("ku"))
                .andExpect(jsonPath("$.data.paths").isArray());
    }

    @Test
    void visitorsCanSubmitContactMessages() throws Exception {
        mockMvc.perform(post("/api/v1/contact/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API Contract Test",
                                  "email": "visitor@example.com",
                                  "subject": "Question",
                                  "message": "Please send more information.",
                                  "locale": "ckb"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").value("NEW"));
    }

    @Test
    void visitorsCanSubmitBothDonationFlows() throws Exception {
        mockMvc.perform(post("/api/v1/donations/financial")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "donorName": "Donor",
                                  "email": "donor@example.com",
                                  "amount": 25.50,
                                  "currency": "USD",
                                  "paymentMethod": "BANK_TRANSFER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(post("/api/v1/donations/archive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "donorName": "Archive Donor",
                                  "email": "archive@example.com",
                                  "materialType": "PHOTOGRAPH",
                                  "title": "Historic photograph",
                                  "description": "A photograph offered to the institute archive."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
}
