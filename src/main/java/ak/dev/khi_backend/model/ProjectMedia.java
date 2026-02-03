package ak.dev.khi_backend.model;

import ak.dev.khi_backend.enums.ProjectMediaType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "project_media",
        indexes = {
                @Index(name = "idx_pm_project", columnList = "project_id,sort_order"),
                @Index(name = "idx_pm_type", columnList = "media_type")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore // ✅ prevents recursion if entity accidentally serialized
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private ProjectMediaType mediaType;

    @Column(length = 1024)
    private String url;


    @Column(length = 255)
    private String caption;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
