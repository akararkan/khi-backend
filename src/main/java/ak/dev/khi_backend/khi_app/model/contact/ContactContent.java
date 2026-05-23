package ak.dev.khi_backend.khi_app.model.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * ContactContent — Bilingual embeddable for the Contact entity.
 *
 * Holds all language-specific text fields for a single language version
 * (CKB = Sorani or KMR = Kurmanji) of a contact page, including a Tiptap
 * HTML {@code description} that is the single source of truth for all
 * embedded media (image, video, voice, document, or any other file).
 */
@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ContactContent {

    /** Page title in this language. */
    @Column(length = 300)
    private String title;

    /** Short subtitle / call-to-action text. */
    @Column(length = 500)
    private String subtitle;

    /** Physical address in this language. */
    @Column(length = 500)
    private String address;

    /** Working / office hours in this language. */
    @Column(name = "working_hours", length = 300)
    private String workingHours;

    /**
     * Tiptap HTML description — full editor output for this language.
     * Inline {@code <img>}, {@code <video>}, {@code <audio>}, and
     * {@code <a href>} tags reference S3 URLs; any base64 payload pasted
     * by the editor is rewritten to S3 by
     * {@link ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor}
     * before persistence.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
