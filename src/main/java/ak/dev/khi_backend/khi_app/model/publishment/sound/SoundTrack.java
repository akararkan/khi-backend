package ak.dev.khi_backend.khi_app.model.publishment.sound;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.SoundType;
import ak.dev.khi_backend.khi_app.enums.publishment.TrackState;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(
        name = "sound_tracks",
        indexes = {
                @Index(name = "idx_soundtrack_language", columnList = "language"),
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

    // Basic info
    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    @Column(length = 4000)
    private String description;

    /**
     * reading: "group of people" or "single person" or a name.
     * keep it as string as you requested.
     */
    @Column(length = 255)
    private String reading;

    @Enumerated(EnumType.STRING)
    @Column(name = "sound_type", nullable = false, length = 20)
    private SoundType soundType; // LAWK, HAIRAN

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Language language; // CKB, KMR

    /**
     * Locations (collection of strings) - CHANGED FROM SINGLE STRING TO COLLECTION
     */
    @ElementCollection
    @CollectionTable(
            name = "sound_track_locations",
            joinColumns = @JoinColumn(name = "sound_track_id")
    )
    @Column(name = "location", nullable = false, length = 255)
    private Set<String> locations = new HashSet<>();

    @Column(length = 255)
    private String director;

    @Column(name = "is_institute_project", nullable = false)
    private boolean isThisProjectOfInstitute;

    @Enumerated(EnumType.STRING)
    @Column(name = "track_state", nullable = false, length = 10)
    private TrackState trackState; // SINGLE, MULTI

    /**
     * Keywords (collection of strings)
     */
    @ElementCollection
    @CollectionTable(
            name = "sound_track_keywords",
            joinColumns = @JoinColumn(name = "sound_track_id")
    )
    @Column(name = "keyword", nullable = false, length = 100)
    private Set<String> keywords = new HashSet<>();

    /**
     * Tags (collection of strings)
     */
    @ElementCollection
    @CollectionTable(
            name = "sound_track_tags",
            joinColumns = @JoinColumn(name = "sound_track_id")
    )
    @Column(name = "tag", nullable = false, length = 60)
    private Set<String> tags = new HashSet<>();

    /**
     * Files: for SINGLE -> 1 row in sound_track_files
     * for MULTI  -> many rows in sound_track_files
     */
    @OneToMany(
            mappedBy = "soundTrack",
            cascade = CascadeType.ALL,
            orphanRemoval = true
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

    // Helper methods (clean add/remove)
    public void addFile(SoundTrackFile file) {
        files.add(file);
        file.setSoundTrack(this);
    }

    public void removeFile(SoundTrackFile file) {
        files.remove(file);
        file.setSoundTrack(null);
    }
}