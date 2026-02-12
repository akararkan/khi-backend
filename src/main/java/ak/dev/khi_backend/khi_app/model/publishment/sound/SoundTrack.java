package ak.dev.khi_backend.khi_app.model.publishment.sound;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.SoundType;
import ak.dev.khi_backend.khi_app.enums.publishment.TrackState;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(
        name = "sound_tracks",
        indexes = {
                @Index(name = "idx_soundtrack_type", columnList = "sound_type"),
                @Index(name = "idx_soundtrack_state", columnList = "track_state"),
                @Index(name = "idx_soundtrack_created_at", columnList = "created_at"),
                @Index(name = "idx_soundtrack_updated_at", columnList = "updated_at")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoundTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "sound_type", nullable = false, length = 20)
    private SoundType soundType; // LAWK, HAIRAN

    // ✅ BILINGUAL SUPPORT: Which languages are active
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "sound_track_content_languages",
            joinColumns = @JoinColumn(name = "sound_track_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    // ✅ CKB (Sorani) Content
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "title_ckb", length = 200)),
            @AttributeOverride(name = "description", column = @Column(name = "description_ckb", columnDefinition = "TEXT")),
            @AttributeOverride(name = "reading", column = @Column(name = "reading_ckb", length = 255))
    })
    private SoundTrackContent ckbContent;

    // ✅ KMR (Kurmanji) Content
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "title_kmr", length = 200)),
            @AttributeOverride(name = "description", column = @Column(name = "description_kmr", columnDefinition = "TEXT")),
            @AttributeOverride(name = "reading", column = @Column(name = "reading_kmr", length = 255))
    })
    private SoundTrackContent kmrContent;

    // ✅ Shared fields (not language-specific)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "sound_track_locations",
            joinColumns = @JoinColumn(name = "sound_track_id")
    )
    @Column(name = "location", nullable = false, length = 255)
    @Builder.Default
    private Set<String> locations = new LinkedHashSet<>();

    @Column(length = 255)
    private String director;

    @Column(name = "is_institute_project", nullable = false)
    private boolean isThisProjectOfInstitute;

    @Enumerated(EnumType.STRING)
    @Column(name = "track_state", nullable = false, length = 10)
    private TrackState trackState; // SINGLE, MULTI

    // ✅ CKB Keywords
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sound_track_keywords_ckb", joinColumns = @JoinColumn(name = "sound_track_id"))
    @Column(name = "keyword_ckb", nullable = false, length = 100)
    private Set<String> keywordsCkb = new LinkedHashSet<>();

    // ✅ KMR Keywords
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sound_track_keywords_kmr", joinColumns = @JoinColumn(name = "sound_track_id"))
    @Column(name = "keyword_kmr", nullable = false, length = 100)
    private Set<String> keywordsKmr = new LinkedHashSet<>();

    // ✅ CKB Tags
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sound_track_tags_ckb", joinColumns = @JoinColumn(name = "sound_track_id"))
    @Column(name = "tag_ckb", nullable = false, length = 60)
    private Set<String> tagsCkb = new LinkedHashSet<>();

    // ✅ KMR Tags
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sound_track_tags_kmr", joinColumns = @JoinColumn(name = "sound_track_id"))
    @Column(name = "tag_kmr", nullable = false, length = 60)
    private Set<String> tagsKmr = new LinkedHashSet<>();

    // ✅ Audio files
    @Builder.Default
    @OneToMany(
            mappedBy = "soundTrack",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderColumn(name = "file_order")
    private List<SoundTrackFile> files = new ArrayList<>();

    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper methods
    public void addFile(SoundTrackFile file) {
        files.add(file);
        file.setSoundTrack(this);
    }

    public void removeFile(SoundTrackFile file) {
        files.remove(file);
        file.setSoundTrack(null);
    }
}