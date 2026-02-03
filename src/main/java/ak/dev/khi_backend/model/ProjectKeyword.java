package ak.dev.khi_backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "project_keywords",
        uniqueConstraints = @UniqueConstraint(name = "uq_project_keywords_name", columnNames = "name")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, length = 191)
    private String name;
}
