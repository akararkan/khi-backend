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

/**
 * Project — Tiptap migration.
 *
 * The {@code description} field on {@link ProjectContentBlock} now holds the
 * full Tiptap HTML output. The old {@code project_media} table and the
 * {@code contentsCkb} / {@code contentsKmr} free-text content tags have been
 * removed — inline media lives inside the HTML, and any named "section" can
 * be expressed as a heading inside the editor.
 */
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

    @Column(name = "cover_url", length = 1024)
    private String coverUrl;

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

    @Column(name = "project_type_ckb", length = 128)
    private String projectTypeCkb;

    @Column(name = "project_type_kmr", length = 128)
    private String projectTypeKmr;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.ONGOING;

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

    @Column(name = "project_date")
    private LocalDate projectDate;

    // project_media table dropped — inline images / audio / video now live
    // inside the Tiptap HTML stored in ckbContent.description and
    // kmrContent.description. Uploads go through POST /api/v1/media/upload.
}
