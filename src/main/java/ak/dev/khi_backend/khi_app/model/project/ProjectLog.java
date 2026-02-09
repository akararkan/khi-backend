package ak.dev.khi_backend.khi_app.model.project;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which project changed */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** What happened */
    @Column(nullable = false, length = 50)
    private String action;
    // CREATE, UPDATE, COMPLETE, ADD_MEDIA, REMOVE_MEDIA, etc.

    /** Field that changed (optional) */
    @Column(length = 50)
    private String fieldName;

    /** Old value (optional) */
    @Column(columnDefinition = "text")
    private String oldValue;

    /** New value (optional) */
    @Column(columnDefinition = "text")
    private String newValue;

    /** When */
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
