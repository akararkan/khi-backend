package ak.dev.khi_backend.khi_app.model.publishment.album_of_memories;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.AlbumType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "albums_of_memories")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AlbumOfMemories {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cover image URL
    @Column(name = "cover_url", nullable = false, columnDefinition = "TEXT")
    private String coverUrl;

    // ✅ CKB (Sorani) Content
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "title_ckb", length = 250)),
            @AttributeOverride(name = "description", column = @Column(name = "description_ckb", columnDefinition = "TEXT")),
            @AttributeOverride(name = "location", column = @Column(name = "location_ckb", length = 200))
    })
    private AlbumContent ckbContent;

    // ✅ KMR (Kurmanji) Content
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "title_kmr", length = 250)),
            @AttributeOverride(name = "description", column = @Column(name = "description_kmr", columnDefinition = "TEXT")),
            @AttributeOverride(name = "location", column = @Column(name = "location_kmr", length = 200))
    })
    private AlbumContent kmrContent;

    // Album type: AUDIO or VIDEO
    @Enumerated(EnumType.STRING)
    @Column(name = "album_type", nullable = false, length = 20)
    private AlbumType albumType;

    // Primary file format (mp3, mp4, etc.)
    @Column(name = "file_format", length = 20)
    private String fileFormat;

    // CD number (if physical album)
    @Column(name = "cd_number")
    private Integer cdNumber;

    // Total number of tracks in this album
    @Column(name = "number_of_tracks")
    private Integer numberOfTracks;

    // Year of publication
    @Column(name = "year_of_publishment")
    private Integer yearOfPublishment;

    // Languages of the album content (CKB, KMR, or both)
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "album_content_languages", joinColumns = @JoinColumn(name = "album_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    // ✅ EAGER FETCH - CKB tags
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "album_tags_ckb", joinColumns = @JoinColumn(name = "album_id"))
    @Column(name = "tag_ckb", nullable = false, length = 80)
    private Set<String> tagsCkb = new LinkedHashSet<>();

    // ✅ EAGER FETCH - KMR tags
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "album_tags_kmr", joinColumns = @JoinColumn(name = "album_id"))
    @Column(name = "tag_kmr", nullable = false, length = 80)
    private Set<String> tagsKmr = new LinkedHashSet<>();

    // ✅ EAGER FETCH - CKB keywords
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "album_keywords_ckb", joinColumns = @JoinColumn(name = "album_id"))
    @Column(name = "keyword_ckb", nullable = false, length = 120)
    private Set<String> keywordsCkb = new LinkedHashSet<>();

    // ✅ EAGER FETCH - KMR keywords
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "album_keywords_kmr", joinColumns = @JoinColumn(name = "album_id"))
    @Column(name = "keyword_kmr", nullable = false, length = 120)
    private Set<String> keywordsKmr = new LinkedHashSet<>();

    // ✅ LAZY FETCH - Multiple media files (audio/video tracks)
    @Builder.Default
    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("trackNumber ASC")
    private List<AlbumMedia> media = new ArrayList<>();

    // Attachment URL (for demonstration/advertisement - PDF or video)
    @Column(name = "attachment_url", columnDefinition = "TEXT")
    private String attachmentUrl;

    // ✅ NEW: Attachment external link
    @Column(name = "attachment_external_url", columnDefinition = "TEXT")
    private String attachmentExternalUrl;

    // ✅ NEW: Attachment embed link
    @Column(name = "attachment_embed_url", columnDefinition = "TEXT")
    private String attachmentEmbedUrl;

    // Attachment type (pdf, mp4, etc.)
    @Column(name = "attachment_type", length = 20)
    private String attachmentType;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
