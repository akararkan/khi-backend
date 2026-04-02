package ak.dev.khi_backend.khi_app.model.service;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ServiceMediaCollection — A named, typed group of media files for a {@link Service}.
 *
 * ─── Purpose ──────────────────────────────────────────────────────────────────
 *  Organises media into logical galleries so the frontend can render each group
 *  in its own tab / section:
 *
 *    Service "Spring Training 2025"
 *      ├─ Collection: "Event Photos"   (IMAGE)
 *      ├─ Collection: "Recap Videos"   (VIDEO)
 *      └─ Collection: "Interviews"     (AUDIO)
 *
 * ─── Media Types ──────────────────────────────────────────────────────────────
 *  Stored as a {@link MediaType} enum (IMAGE | VIDEO | AUDIO).
 *  All files inside a collection share the same declared type, although the
 *  actual MIME is preserved at the {@link ServiceMediaFile} level.
 *
 * ─── Ordering ─────────────────────────────────────────────────────────────────
 *  {@link #sortOrder} controls the display order of collections on the page.
 *  Files within a collection are ordered by {@link ServiceMediaFile#sortOrder}.
 */
@Entity
@Table(
        name = "service_media_collections",
        indexes = {
                @Index(name = "idx_smc_service_id",  columnList = "service_id"),
                @Index(name = "idx_smc_media_type",  columnList = "media_type"),
                @Index(name = "idx_smc_sort_order",  columnList = "sort_order")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceMediaCollection {

    // ─── Media Type Enum ──────────────────────────────────────────────────────

    public enum MediaType {
        IMAGE, VIDEO, AUDIO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Collection Meta ──────────────────────────────────────────────────────

    /**
     * Human-readable collection name shown in the admin panel and public UI.
     * Examples: "Event Photos", "Recap Videos", "Interviews"
     */
    @NotBlank
    @Column(name = "collection_name", nullable = false, length = 200)
    private String collectionName;

    /**
     * Declared media type for all files in this collection.
     * IMAGE | VIDEO | AUDIO
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType;

    /** Display order among sibling collections on the same service. */
    @Builder.Default
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ─── Files ────────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<ServiceMediaFile> files = new ArrayList<>();

    // ─── Parent ───────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Service service;

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public void addFile(ServiceMediaFile file) {
        files.add(file);
        file.setCollection(this);
    }

    public void removeFile(ServiceMediaFile file) {
        files.remove(file);
        file.setCollection(null);
    }

    public boolean isImage() { return mediaType == MediaType.IMAGE; }
    public boolean isVideo() { return mediaType == MediaType.VIDEO; }
    public boolean isAudio() { return mediaType == MediaType.AUDIO; }
}