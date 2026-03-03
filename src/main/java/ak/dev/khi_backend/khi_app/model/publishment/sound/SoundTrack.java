package ak.dev.khi_backend.khi_app.model.publishment.sound;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.TrackState;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * SoundTrack — Sound / audio publishment entity.
 *
 * ─── Track States ─────────────────────────────────────────────────────────────
 *
 *  SINGLE
 *    A single audio track (one file or one link). Cannot be an album.
 *
 *  MULTI
 *    A collection of multiple audio tracks (e.g. a full album, a series of
 *    readings). The files list contains all the individual tracks.
 *
 *    MULTI can optionally be an "Album of Memories":
 *      isAlbumOfMemories = true  → memorial / retrospective audio collection
 *      isAlbumOfMemories = false → regular multi-track sound (e.g. a music album)
 *
 *    Always false for SINGLE state.
 *
 * ─── Topic ────────────────────────────────────────────────────────────────────
 *  Topic is a @ManyToOne relation to PublishmentTopic (entityType = "SOUND").
 *  It is NOT a free-text column. This allows:
 *    - Reuse of the same topic across many sound records
 *    - Bilingual topic names (CKB + KMR) managed in one place
 *    - Frontend autocomplete from the topic table
 *
 * ─── Sound Type ───────────────────────────────────────────────────────────────
 *  soundType remains a free-text String (e.g. "LAWK", "HAIRAN") as before.
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
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoundTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Cover Image ──────────────────────────────────────────────────────────

    @Column(name = "cover_url", length = 1000)      // ← column is "cover_url"
    private String ckbCoverUrl;                       //   but field says "ckb"

    @Column(name = "ckb_cover_url", length = 1000)  // ← column is "ckb_cover_url"
    private String kmrCoverUrl;                       //   but field says "kmr"

    @Column(name = "hover_cover_url", length = 1000)
    private String hoverCoverUrl;

    // ─── Sound Type (free-text, e.g. "LAWK", "HAIRAN") ───────────────────────

    @Column(name = "sound_type", nullable = false, length = 100)
    private String soundType;

    // ─── Track State: SINGLE or MULTI ─────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "track_state", nullable = false, length = 10)
    private TrackState trackState;

    /**
     * Is this a "Album of Memories" sound collection?
     *
     * Only meaningful when trackState = MULTI.
     * When true  → memorial / retrospective multi-track collection.
     * When false → regular multi-track sound (music album, readings, etc.).
     *
     * Always false for SINGLE state.
     */
    @Builder.Default
    @Column(name = "is_album_of_memories", nullable = false)
    private boolean albumOfMemories = false;

    // ─── Topic (ManyToOne → PublishmentTopic) ─────────────────────────────────

    /**
     * The topic / subject of this sound record.
     * Points to PublishmentTopic where entityType = "SOUND".
     * Nullable — a track may have no topic assigned yet.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private PublishmentTopic topic;

    // ─── Languages ────────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "sound_track_content_languages",
            joinColumns = @JoinColumn(name = "sound_track_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    // ─── CKB (Sorani) Content ─────────────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",       column = @Column(name = "title_ckb",       length = 200)),
            @AttributeOverride(name = "description", column = @Column(name = "description_ckb", columnDefinition = "TEXT")),
            @AttributeOverride(name = "reading",     column = @Column(name = "reading_ckb",     length = 255))
    })
    private SoundTrackContent ckbContent;

    // ─── KMR (Kurmanji) Content ───────────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",       column = @Column(name = "title_kmr",       length = 200)),
            @AttributeOverride(name = "description", column = @Column(name = "description_kmr", columnDefinition = "TEXT")),
            @AttributeOverride(name = "reading",     column = @Column(name = "reading_kmr",     length = 255))
    })
    private SoundTrackContent kmrContent;

    // ─── Shared Fields ────────────────────────────────────────────────────────

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
    private boolean thisProjectOfInstitute;

    // ─── CKB Keywords ─────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sound_track_keywords_ckb", joinColumns = @JoinColumn(name = "sound_track_id"))
    @Column(name = "keyword_ckb", nullable = false, length = 100)
    private Set<String> keywordsCkb = new LinkedHashSet<>();

    // ─── KMR Keywords ─────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sound_track_keywords_kmr", joinColumns = @JoinColumn(name = "sound_track_id"))
    @Column(name = "keyword_kmr", nullable = false, length = 100)
    private Set<String> keywordsKmr = new LinkedHashSet<>();

    // ─── CKB Tags ─────────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sound_track_tags_ckb", joinColumns = @JoinColumn(name = "sound_track_id"))
    @Column(name = "tag_ckb", nullable = false, length = 60)
    private Set<String> tagsCkb = new LinkedHashSet<>();

    // ─── KMR Tags ─────────────────────────────────────────────────────────────

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sound_track_tags_kmr", joinColumns = @JoinColumn(name = "sound_track_id"))
    @Column(name = "tag_kmr", nullable = false, length = 60)
    private Set<String> tagsKmr = new LinkedHashSet<>();

    // ─── Audio Files ──────────────────────────────────────────────────────────

    @Builder.Default
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

    // ─── Helper Methods ───────────────────────────────────────────────────────

    public void addFile(SoundTrackFile file) {
        files.add(file);
        file.setSoundTrack(this);
    }

    public void removeFile(SoundTrackFile file) {
        files.remove(file);
        file.setSoundTrack(null);
    }

    /** Convenience: true only when MULTI and flagged as album of memories. */
    public boolean isMultiAlbumOfMemories() {
        return trackState == TrackState.MULTI && albumOfMemories;
    }
}
