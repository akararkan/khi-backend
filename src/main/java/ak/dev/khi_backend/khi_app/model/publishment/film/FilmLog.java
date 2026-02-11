package ak.dev.khi_backend.khi_app.model.publishment.film;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "film_logs", indexes = {
        @Index(name = "idx_film_log_film_id", columnList = "film_id"),
        @Index(name = "idx_film_log_action", columnList = "action"),
        @Index(name = "idx_film_log_timestamp", columnList = "timestamp")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FilmLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The film this log belongs to (nullable — film may be deleted)
    @Column(name = "film_id")
    private Long filmId;

    // Film title snapshot at the time of action (for reference after deletion)
    @Column(name = "film_title", length = 300)
    private String filmTitle;

    // Action performed: CREATED, UPDATED, DELETED
    @Column(name = "action", nullable = false, length = 30)
    private String action;

    // What changed (optional detail)
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    // Who performed the action (optional, for future auth integration)
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