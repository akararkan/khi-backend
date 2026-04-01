package ak.dev.khi_backend.khi_app.model.project;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "project_contents",
        uniqueConstraints = @UniqueConstraint(name = "uq_project_contents_name", columnNames = "name")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, length = 128)
    private String name;
}
