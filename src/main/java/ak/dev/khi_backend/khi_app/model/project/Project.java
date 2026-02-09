package ak.dev.khi_backend.khi_app.model.project;

import ak.dev.khi_backend.khi_app.enums.Language;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "projects",
        indexes = {
                @Index(name = "idx_projects_title", columnList = "title"),
                @Index(name = "idx_projects_type", columnList = "project_type"),
                @Index(name = "idx_projects_lang", columnList = "language"),
                @Index(name = "idx_projects_date", columnList = "project_date")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // cover: image cover of the project
    @Column(name = "cover_url", length = 1024)
    private String coverUrl;


    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;


    @Column(name = "project_type", nullable = false, length = 64)
    private String projectType;

    // content: dynamic values (many-to-many)
    @ManyToMany
    @JoinTable(
            name = "project_content_map",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "content_id")
    )
    @Builder.Default
    private Set<ProjectContent> contents = new HashSet<>();

    // tags: multi strings
    @ManyToMany
    @JoinTable(
            name = "project_tag_map",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<ProjectTag> tags = new HashSet<>();

    // keywords: multi strings for search variations
    @ManyToMany
    @JoinTable(
            name = "project_keyword_map",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id")
    )
    @Builder.Default
    private Set<ProjectKeyword> keywords = new HashSet<>();

    // date
    @Column(name = "project_date")
    private LocalDate projectDate;


    @Column(length = 255)
    private String location;

    @Column(nullable = false, length = 10)
    private Language language;

    // media: 1 project -> many media items
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private Set<ProjectMedia> media = new HashSet<>();

    // timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper methods (optional but useful)
    public void addMedia(ProjectMedia m) {
        media.add(m);
        m.setProject(this);
    }

    public void removeMedia(ProjectMedia m) {
        media.remove(m);
        m.setProject(null);
    }
}
