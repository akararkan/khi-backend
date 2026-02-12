package ak.dev.khi_backend.khi_app.model.publishment.writing;

import ak.dev.khi_backend.khi_app.enums.publishment.WritingFileFormat;
import jakarta.persistence.*;
import lombok.*;

/**
 * Embeddable content for a single language (CKB or KMR)
 * Contains all language-specific fields
 */
@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class WritingContent {

    /**
     * Book title in this language
     */
    @Column(name = "title", length = 300)
    private String title;

    /**
     * Book description in this language
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Writer/Author name in this language
     */
    @Column(name = "writer", length = 200)
    private String writer;

    /**
     * Cover page URL for this language version
     * Optional - some books may not have a cover
     */
    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    /**
     * The actual book file URL (PDF, DOCX, etc.) for this language
     */
    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    /**
     * File format for this language version
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", length = 20)
    private WritingFileFormat fileFormat;

    /**
     * File size in bytes for this language version
     */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /**
     * Number of pages in this language version
     */
    @Column(name = "page_count")
    private Integer pageCount;

    /**
     * Book genre/category in this language
     * Example: "Novel", "Short Stories", "Essay Collection"
     */
    @Column(name = "genre", length = 150)
    private String genre;
}