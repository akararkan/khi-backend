package ak.dev.khi_backend.khi_app.model.about;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "about_blocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AboutBlock {

    public enum ContentType {
        TEXT, IMAGE, VIDEO, AUDIO, GALLERY, QUOTE, STATS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type")
    private ContentType contentType;

    @Column(name = "sequence")
    private Integer sequence = 0;

    // For TEXT: the actual content
    // For others: caption/description
    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    // Media URL (S3, CDN, or local path)
    @Column(name = "media_url")
    private String mediaUrl;

    // Thumbnail URL for videos/images
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "title")
    private String title;

    @Column(name = "alt_text")
    private String altText; // For accessibility

    // Additional metadata (duration for video/audio, dimensions for images, etc.)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "about_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private About about;

    // Helper methods for specific types
    public boolean isText() { return contentType == ContentType.TEXT; }
    public boolean isImage() { return contentType == ContentType.IMAGE; }
    public boolean isVideo() { return contentType == ContentType.VIDEO; }
    public boolean isAudio() { return contentType == ContentType.AUDIO; }
}
