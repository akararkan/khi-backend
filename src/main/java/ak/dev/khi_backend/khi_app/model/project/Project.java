package ak.dev.khi_backend.khi_app.model.project;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.model.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "projects",
        indexes = {
                @Index(name = "idx_projects_title_ckb", columnList = "title_ckb"),
                @Index(name = "idx_projects_title_kmr", columnList = "title_kmr"),
                @Index(name = "idx_projects_type", columnList = "project_type"),
                @Index(name = "idx_projects_date", columnList = "project_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // cover: image cover of the project
    @Column(name = "cover_url", length = 1024)
    private String coverUrl;

    // ✅ CKB (Sorani) Content
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "title_ckb", length = 255)),
            @AttributeOverride(name = "description", column = @Column(name = "description_ckb", columnDefinition = "TEXT")),
            @AttributeOverride(name = "location", column = @Column(name = "location_ckb", length = 255))
    })
    private ProjectContentBlock ckbContent;

    // ✅ KMR (Kurmanji) Content
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "title_kmr", length = 255)),
            @AttributeOverride(name = "description", column = @Column(name = "description_kmr", columnDefinition = "TEXT")),
            @AttributeOverride(name = "location", column = @Column(name = "location_kmr", length = 255))
    })
    private ProjectContentBlock kmrContent;

    @Column(name = "project_type", nullable = false, length = 64)
    private String projectType;

    // ✅ Which languages are available for this project
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "project_content_languages",
            joinColumns = @JoinColumn(name = "project_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    // ----------------------------
    // ✅ CONTENTS (per language)
    // ----------------------------

    @ManyToMany
    @JoinTable(
            name = "project_content_map_ckb",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "content_id")
    )
    @Builder.Default
    private Set<ProjectContent> contentsCkb = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "project_content_map_kmr",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "content_id")
    )
    @Builder.Default
    private Set<ProjectContent> contentsKmr = new LinkedHashSet<>();

    // ----------------------------
    // ✅ TAGS (per language)
    // ----------------------------

    @ManyToMany
    @JoinTable(
            name = "project_tag_map_ckb",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<ProjectTag> tagsCkb = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "project_tag_map_kmr",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<ProjectTag> tagsKmr = new LinkedHashSet<>();

    // ----------------------------
    // ✅ KEYWORDS (per language)
    // ----------------------------

    @ManyToMany
    @JoinTable(
            name = "project_keyword_map_ckb",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id")
    )
    @Builder.Default
    private Set<ProjectKeyword> keywordsCkb = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "project_keyword_map_kmr",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id")
    )
    @Builder.Default
    private Set<ProjectKeyword> keywordsKmr = new LinkedHashSet<>();

    // date
    @Column(name = "project_date")
    private LocalDate projectDate;

    // media: 1 project -> many media items
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private Set<ProjectMedia> media = new LinkedHashSet<>();

    // Helper methods
    public void addMedia(ProjectMedia m) {
        media.add(m);
        m.setProject(this);
    }

    public void removeMedia(ProjectMedia m) {
        media.remove(m);
        m.setProject(null);
    }
}
