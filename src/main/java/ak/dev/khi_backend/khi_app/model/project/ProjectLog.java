package ak.dev.khi_backend.khi_app.model.project;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "project_log",
        indexes = {
                @Index(name = "idx_project_log_project_id", columnList = "project_id"),
                @Index(name = "idx_project_log_action",     columnList = "action"),
                @Index(name = "idx_project_log_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Which project this log entry belongs to.
     *
     * ✅ @OnDelete(CASCADE) — tells Hibernate to emit the DDL fragment
     *    "ON DELETE CASCADE" on this FK column.  PostgreSQL will then
     *    automatically remove all related log rows whenever the parent
     *    project row is deleted, so we never hit the FK-violation error.
     *
     *    nullable = true  — if you ever need to keep orphan logs after
     *    a project is gone, switch this to nullable and drop the cascade;
     *    but CASCADE is correct for our "delete project → delete its logs" rule.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name       = "project_id",
            nullable   = false,
            foreignKey = @ForeignKey(name = "fk_project_log_project")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)   // ← THE FIX
    private Project project;

    /** CREATE | UPDATE | DELETE | ADD_MEDIA | REMOVE_MEDIA | … */
    @Column(nullable = false, length = 50)
    private String action;

    /** The field that changed (optional – SUMMARY for general entries) */
    @Column(name = "field_name", length = 50)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}