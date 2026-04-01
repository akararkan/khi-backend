package ak.dev.khi_backend.khi_app.model.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * ContactContent — Bilingual embeddable for the Contact entity.
 *
 * Holds all language-specific text fields for a single language version
 * (CKB = Sorani or KMR = Kurmanji) of a contact page.
 *
 * Follows the same pattern as {@link ak.dev.khi_backend.khi_app.model.about.AboutContent}.
 */
@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ContactContent {

    /** Page title in this language — e.g. "پەیوەندیمان پێوە بکە" */
    @Column(length = 300)
    private String title;

    /** Short subtitle / call-to-action text */
    @Column(length = 500)
    private String subtitle;

    /** Physical address in this language */
    @Column(length = 500)
    private String address;

    /** Working / office hours in this language — e.g. "یەکشەممە - پێنجشەممە، ٩:٠٠ - ١٦:٠٠" */
    @Column(name = "working_hours", length = 300)
    private String workingHours;
}