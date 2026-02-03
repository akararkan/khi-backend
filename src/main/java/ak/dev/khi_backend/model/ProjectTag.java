package ak.dev.khi_backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "project_tags",
        uniqueConstraints = @UniqueConstraint(name = "uq_project_tags_name", columnNames = "name")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;
}
