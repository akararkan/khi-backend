package ak.dev.khi_backend.khi_app.model.publishment.image;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "image_collection_logs", indexes = {
        @Index(name = "idx_img_log_collection_id", columnList = "image_collection_id"),
        @Index(name = "idx_img_log_action", columnList = "action"),
        @Index(name = "idx_img_log_timestamp", columnList = "timestamp")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ImageCollectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_collection_id")
    private Long imageCollectionId;

    @Column(name = "collection_title", length = 300)
    private String collectionTitle;

    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "performed_by", length = 150)
    private String performedBy;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    public void prePersist() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}