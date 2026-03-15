package ak.dev.khi_backend.khi_app.model.project;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.project.ProjectStatus;
import ak.dev.khi_backend.khi_app.model.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "projects",
        indexes = {
                @Index(name = "idx_projects_type_ckb", columnList = "project_type_ckb"),
                @Index(name = "idx_projects_type_kmr", columnList = "project_type_kmr"),
                @Index(name = "idx_projects_status",   columnList = "status"),
                @Index(name = "idx_projects_date",     columnList = "project_date")
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

    // ─────────────────────────────────────────────
    // Cover Image
    // ─────────────────────────────────────────────

    @Column(name = "cover_url", length = 1024)
    private String coverUrl;

    // ─────────────────────────────────────────────
    // Embedded bilingual content blocks
    // ─────────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",       column = @Column(name = "title_ckb",       length = 255)),
            @AttributeOverride(name = "description", column = @Column(name = "description_ckb", columnDefinition = "TEXT")),
            @AttributeOverride(name = "location",    column = @Column(name = "location_ckb",    length = 255))
    })
    private ProjectContentBlock ckbContent;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title",       column = @Column(name = "title_kmr",       length = 255)),
            @AttributeOverride(name = "description", column = @Column(name = "description_kmr", columnDefinition = "TEXT")),
            @AttributeOverride(name = "location",    column = @Column(name = "location_kmr",    length = 255))
    })
    private ProjectContentBlock kmrContent;

    // ─────────────────────────────────────────────
    // Project type (bilingual label)
    // ─────────────────────────────────────────────

    @Column(name = "project_type_ckb", length = 128)
    private String projectTypeCkb;

    @Column(name = "project_type_kmr", length = 128)
    private String projectTypeKmr;

    // ─────────────────────────────────────────────
    // Status
    // ─────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.ONGOING;

    // ─────────────────────────────────────────────
    // Content Languages
    //
    // @BatchSize: Hibernate loads ALL languages for the
    // current page of projects in ONE IN-query instead of
    // one query per project (eliminates N+1).
    //
    // FetchType changed to LAZY — no longer needed as EAGER
    // because @BatchSize + the service's @Transactional
    // guarantees loading within the open session.
    // ─────────────────────────────────────────────

    @Builder.Default
    @BatchSize(size = 50)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "project_content_languages",
            joinColumns = @JoinColumn(name = "project_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private Set<Language> contentLanguages = new LinkedHashSet<>();

    // ─────────────────────────────────────────────
    // Contents (per language)
    //
    // @BatchSize on every collection:
    //   Without it → Hibernate fires 1 SELECT per project per collection
    //                = N×8 queries for N projects (N+1 problem)
    //   With it    → Hibernate fires 1 IN-query per collection type
    //                = exactly 8 queries for any page size
    // ─────────────────────────────────────────────

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(
            name = "project_content_map_ckb",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "content_id")
    )
    @Builder.Default
    private Set<ProjectContent> contentsCkb = new LinkedHashSet<>();

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(
            name = "project_content_map_kmr",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "content_id")
    )
    @Builder.Default
    private Set<ProjectContent> contentsKmr = new LinkedHashSet<>();

    // ─────────────────────────────────────────────
    // Tags (per language)
    // ─────────────────────────────────────────────

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(
            name = "project_tag_map_ckb",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<ProjectTag> tagsCkb = new LinkedHashSet<>();

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(
            name = "project_tag_map_kmr",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<ProjectTag> tagsKmr = new LinkedHashSet<>();

    // ─────────────────────────────────────────────
    // Keywords (per language)
    // ─────────────────────────────────────────────

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(
            name = "project_keyword_map_ckb",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id")
    )
    @Builder.Default
    private Set<ProjectKeyword> keywordsCkb = new LinkedHashSet<>();

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(
            name = "project_keyword_map_kmr",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id")
    )
    @Builder.Default
    private Set<ProjectKeyword> keywordsKmr = new LinkedHashSet<>();

    // ─────────────────────────────────────────────
    // Project Date
    // ─────────────────────────────────────────────

    @Column(name = "project_date")
    private LocalDate projectDate;

    // ─────────────────────────────────────────────
    // Media
    //
    // @BatchSize here means: for 20 projects on a page,
    // Hibernate loads all their media in 1 IN-query,
    // not 20 separate queries.
    // ─────────────────────────────────────────────

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private Set<ProjectMedia> media = new LinkedHashSet<>();

    // ─────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────

    public void addMedia(ProjectMedia m) {
        media.add(m);
        m.setProject(this);
    }

    public void removeMedia(ProjectMedia m) {
        media.remove(m);
        m.setProject(null);
    }
}