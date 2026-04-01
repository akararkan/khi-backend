package ak.dev.khi_backend.khi_app.model.publishment.topic;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;

/**
 * PublishmentTopic — Shared dynamic topic registry.
 *
 * Each publishment type (VIDEO, SOUND, IMAGE, WRITING) has its own topics.
 * Instead of a fixed enum or a loose free-text field, topics live in this
 * table so they can be managed (added, listed, searched) independently.
 *
 * Video  → @ManyToOne PublishmentTopic  (topic_id on the videos table)
 * Sound  → @ManyToOne PublishmentTopic  (topic_id on the sound_tracks table)
 *
 * The `entityType` column separates the topics per publishment so that
 * "Sound topics" and "Video topics" don't mix in one lookup.
 *
 * Bilingual:
 *  - nameCkb  → Sorani  display name
 *  - nameKmr  → Kurmanji display name
 */
@Entity
@Table(
        name = "publishment_topics",
        indexes = {
                @Index(name = "idx_topic_entity_type", columnList = "entity_type"),
                @Index(name = "idx_topic_name_ckb",   columnList = "entity_type, name_ckb"),
                @Index(name = "idx_topic_name_kmr",   columnList = "entity_type, name_kmr")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@BatchSize(size = 50)   // ← ADD THIS
public class PublishmentTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Which publishment type this topic belongs to.
     * Allowed values:  "VIDEO" | "SOUND" | "IMAGE" | "WRITING"
     */
    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType;

    /** Topic name in CKB (Sorani). */
    @Column(name = "name_ckb", length = 300)
    private String nameCkb;

    /** Topic name in KMR (Kurmanji). */
    @Column(name = "name_kmr", length = 300)
    private String nameKmr;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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
