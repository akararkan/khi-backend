package ak.dev.khi_backend.khi_app.model.publishment.sound;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.TrackState;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.*;

/**
 * SoundTrack — Sound / audio publishment entity.
 *
 * ─── @BatchSize strategy ───────────────────────────────────────────────────
 *
 *  All collections changed from EAGER → LAZY + @BatchSize(size = 50).
 *
 *  Without @BatchSize (old EAGER):
 *    Hibernate fires 1 massive LEFT JOIN across all collections
 *    = Cartesian product explosion for N tracks × tags × keywords × files
 *
 *  With @BatchSize (new LAZY):
 *    Q1 : SELECT s   FROM sound_tracks                  WHERE id IN (...)
 *    Q2 : SELECT ... FROM sound_track_content_languages  WHERE sound_track_id IN (...)
 *    Q3 : SELECT ... FROM sound_track_locations          WHERE sound_track_id IN (...)
 *    Q4 : SELECT ... FROM sound_track_keywords_ckb       WHERE sound_track_id IN (...)
 *    Q5 : SELECT ... FROM sound_track_keywords_kmr       WHERE sound_track_id IN (...)
 *    Q6 : SELECT ... FROM sound_track_tags_ckb           WHERE sound_track_id IN (...)
 *    Q7 : SELECT ... FROM sound_track_tags_kmr           WHERE sound_track_id IN (...)
 *    Q8 : SELECT ... FROM sound_track_files              WHERE sound_track_id IN (...)
 *    Q9 : SELECT ... FROM publishment_topics             WHERE id IN (...)  ← @BatchSize on class
 *
 *    9 fast IN-queries for any page size.
 */
@Entity
@Table(
        name = "sound_tracks",
        indexes = {
                @Index(name = "idx_soundtrack_type",       columnList = "sound_type"),
                @Index(name = "idx_soundtrack_state",      columnList = "track_state"),
                @Index(name = "idx_soundtrack_album",      columnList = "is_album_of_memories"),
                @Index(name = "idx_soundtrack_topic",      columnList = "topic_id"),
                @Index(name = "idx_soundtrack_created_at", columnList = "created_at"),
                @Index(name = "idx_soundtrack_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoundTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Cover Images ─────────────────────────────────────────────────────────

    @Column(name = "cover_url", length = 1000)
    private String ckbCoverUrl;

    @Column(name = "ckb_cover_url", length = 1000)
    private String kmrCoverUrl;

    @Column(name = "hover_cover_url", length = 1000)
    private String hoverCoverUrl;

    // ─── Sound Type ───────────────────────────────────────────────────────────

    @Column(name = "sound_type", nullable = false, length = 100)
    private String soundType;

    // ─── Track State ──────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "track_state", nullable = false, length = 10)
    private TrackState trackState;

    @Builder.Default
    @Column(name = "is_album_of_memories", nullable = false)
    private boolean albumOfMemories = false;

    // ─── Topic ────────────────────────────────────────────────────────────────
    //
    // LAZY + @BatchSize on the PublishmentTopic CLASS.
    // For a page of N tracks, Hibernate fires 1 IN-query
    // to load all their topics instead of N queries.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private PublishmentTopic topic;

    // ─── Content Languages ────────────────────────────────────────────────────
    //
    // Changed EAGER → LAZY + @BatchSize.
    // 1 IN-query loads languages for the entire page.

    @Builder.Default
    @BatchSize(size = 50)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "sound_track_content_languages",
            joinColumns = @JoinColumn(name = "sound_track_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    // ─── Embedded bilingual content ───────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",
                    column = @Column(name = "title_ckb",       length = 200)),
            @AttributeOverride(name = "description",
                    column = @Column(name = "description_ckb", columnDefinition = "TEXT")),
            @AttributeOverride(name = "reading",
                    column = @Column(name = "reading_ckb",     length = 255))
    })
    private SoundTrackContent ckbContent;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",
                    column = @Column(name = "title_kmr",       length = 200)),
            @AttributeOverride(name = "description",
                    column = @Column(name = "description_kmr", columnDefinition = "TEXT")),
            @AttributeOverride(name = "reading",
                    column = @Column(name = "reading_kmr",     length = 255))
    })
    private SoundTrackContent kmrContent;

    // ─── Locations ────────────────────────────────────────────────────────────

    @Builder.Default
    @BatchSize(size = 50)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "sound_track_locations",
            joinColumns = @JoinColumn(name = "sound_track_id")
    )
    @Column(name = "location", nullable = false, length = 255)
    private Set<String> locations = new LinkedHashSet<>();

    @Column(length = 255)
    private String director;

    @Column(name = "is_institute_project", nullable = false)
    private boolean thisProjectOfInstitute;

    // ─── CKB Keywords ─────────────────────────────────────────────────────────

    @Builder.Default
    @BatchSize(size = 50)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "sound_track_keywords_ckb",
            joinColumns = @JoinColumn(name = "sound_track_id")
    )
    @Column(name = "keyword_ckb", nullable = false, length = 100)
    private Set<String> keywordsCkb = new LinkedHashSet<>();

    // ─── KMR Keywords ─────────────────────────────────────────────────────────

    @Builder.Default
    @BatchSize(size = 50)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "sound_track_keywords_kmr",
            joinColumns = @JoinColumn(name = "sound_track_id")
    )
    @Column(name = "keyword_kmr", nullable = false, length = 100)
    private Set<String> keywordsKmr = new LinkedHashSet<>();

    // ─── CKB Tags ─────────────────────────────────────────────────────────────

    @Builder.Default
    @BatchSize(size = 50)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "sound_track_tags_ckb",
            joinColumns = @JoinColumn(name = "sound_track_id")
    )
    @Column(name = "tag_ckb", nullable = false, length = 60)
    private Set<String> tagsCkb = new LinkedHashSet<>();

    // ─── KMR Tags ─────────────────────────────────────────────────────────────

    @Builder.Default
    @BatchSize(size = 50)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "sound_track_tags_kmr",
            joinColumns = @JoinColumn(name = "sound_track_id")
    )
    @Column(name = "tag_kmr", nullable = false, length = 60)
    private Set<String> tagsKmr = new LinkedHashSet<>();

    // ─── Audio Files ──────────────────────────────────────────────────────────
    //
    // @BatchSize: for 20 tracks on a page, Hibernate loads ALL
    // their files in 1 IN-query instead of 20 queries.

    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(
            mappedBy = "soundTrack",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderColumn(name = "file_order")
    private List<SoundTrackFile> files = new ArrayList<>();

    // ─── Timestamps ───────────────────────────────────────────────────────────

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

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public void addFile(SoundTrackFile file) {
        files.add(file);
        file.setSoundTrack(this);
    }

    public void removeFile(SoundTrackFile file) {
        files.remove(file);
        file.setSoundTrack(null);
    }

    public boolean isMultiAlbumOfMemories() {
        return trackState == TrackState.MULTI && albumOfMemories;
    }
}