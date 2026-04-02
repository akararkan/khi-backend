package ak.dev.khi_backend.khi_app.model.service;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service — A single institute service (Training, Event, Program, etc.).
 *
 * ─── Bilingual Content ────────────────────────────────────────────────────────
 *  Unlike the About entity (which uses @Embeddable / @AttributeOverrides),
 *  bilingual text lives in a *separate* {@link ServiceContent} table joined by
 *  (service_id, language_code).  This makes adding more languages later trivial.
 *
 *    service_contents WHERE language_code = 'CKB'  → Sorani   version
 *    service_contents WHERE language_code = 'KMR'  → Kurmanji version
 *
 * ─── Media ────────────────────────────────────────────────────────────────────
 *  {@link #coverMediaUrl}  — a single hero / preview asset (image or video)
 *                            shown on the public listing card.
 *
 *  Rich media galleries live in {@link ServiceMediaCollection} →
 *  {@link ServiceMediaFile}.  Each collection groups files by type
 *  (Images, Videos, Audios) with its own sort order.
 *
 * ─── Publish Lifecycle ────────────────────────────────────────────────────────
 *  {@link #publishedAt}  — explicit publish timestamp set by the admin.
 *  {@link #active}       — soft-disable without deleting.
 */
@Entity
@Table(
        name = "services",
        indexes = {
                @Index(name = "idx_service_type",   columnList = "service_type"),
                @Index(name = "idx_service_active", columnList = "active"),
                @Index(name = "idx_service_published_at", columnList = "published_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Core Fields ──────────────────────────────────────────────────────────

    /**
     * Dynamic service type label — free text so admins can define new types
     * without a code change.
     * Examples: "Training", "Event", "Program", "Workshop", "Conference"
     */
    @NotBlank
    @Column(name = "service_type", nullable = false, length = 100)
    private String serviceType;

    /**
     * Physical or virtual location of the service.
     * Examples: "Sulaymaniyah Hall", "Online", "Erbil Campus"
     * Null when location is not applicable.
     */
    @Column(name = "location", length = 200)
    private String location;

    /**
     * Main preview image or video URL (S3 / CDN).
     * Shown on the public service listing card.
     * Optional — card falls back to the first media file when absent.
     */
    @Column(name = "cover_media_url", columnDefinition = "TEXT")
    private String coverMediaUrl;

    /** Soft-delete / visibility toggle.  Defaults to true (visible). */
    @Builder.Default
    @Column(name = "active")
    private boolean active = true;

    /**
     * Explicit publish timestamp set by the admin when going live.
     * Null means the service was never formally published.
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // ─── Bilingual Content ────────────────────────────────────────────────────

    /**
     * CKB + KMR language rows.
     * Unique constraint on (service_id, language_code) prevents duplicates.
     */
    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ServiceContent> contents = new ArrayList<>();

    // ─── Media Collections ────────────────────────────────────────────────────

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<ServiceMediaCollection> mediaCollections = new ArrayList<>();

    // ─── Timestamps ───────────────────────────────────────────────────────────

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ─── Helper Methods ───────────────────────────────────────────────────────

    public void addContent(ServiceContent content) {
        contents.add(content);
        content.setService(this);
    }

    public void removeContent(ServiceContent content) {
        contents.remove(content);
        content.setService(null);
    }

    public void addMediaCollection(ServiceMediaCollection collection) {
        mediaCollections.add(collection);
        collection.setService(this);
    }

    public void removeMediaCollection(ServiceMediaCollection collection) {
        mediaCollections.remove(collection);
        collection.setService(null);
    }
}