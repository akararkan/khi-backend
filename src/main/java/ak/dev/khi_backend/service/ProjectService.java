package ak.dev.khi_backend.service;

import ak.dev.khi_backend.dto.ProjectCreateRequest;
import ak.dev.khi_backend.dto.ProjectMediaCreateRequest;
import ak.dev.khi_backend.enums.ProjectMediaType;
import ak.dev.khi_backend.exceptions.BadRequestException;
import ak.dev.khi_backend.model.Project;
import ak.dev.khi_backend.model.ProjectContent;
import ak.dev.khi_backend.model.ProjectKeyword;
import ak.dev.khi_backend.model.ProjectLog;
import ak.dev.khi_backend.model.ProjectMedia;
import ak.dev.khi_backend.model.ProjectTag;
import ak.dev.khi_backend.repository.ProjectContentRepository;
import ak.dev.khi_backend.repository.ProjectKeywordRepository;
import ak.dev.khi_backend.repository.ProjectLogRepository;
import ak.dev.khi_backend.repository.ProjectMediaRepository;
import ak.dev.khi_backend.repository.ProjectRepository;
import ak.dev.khi_backend.repository.ProjectTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectContentRepository contentRepository;
    private final ProjectTagRepository tagRepository;
    private final ProjectKeywordRepository keywordRepository;
    private final ProjectMediaRepository mediaRepository;
    private final ProjectLogRepository logRepository;

    private final S3Service s3Service;

    /**
     * Use this to reduce "slow" DB transactions:
     * - do S3 uploads OUTSIDE the DB transaction
     * - then do a short DB transaction for save only
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * ======================================================
     * CREATE PROJECT (JSON only - URLs already uploaded)
     * ======================================================
     */
    public Project create(ProjectCreateRequest dto) {
        String traceId = MDC.get("traceId");
        log.info("Creating project | title={} | traceId={}", dto != null ? dto.getTitle() : null, traceId);

        validate(dto);

        // Keep DB transaction short
        Project saved = transactionTemplate.execute(status -> {
            Project project = buildProject(dto);
            attachContents(project, dto.getContents());
            attachTags(project, dto.getTags());
            attachKeywords(project, dto.getKeywords());
            attachMediaFromDto(project, dto.getMedia());

            Project p = projectRepository.save(project);

            // ✅ Add Project Log (NO extra method)
            logRepository.save(ProjectLog.builder()
                    .project(p)
                    .action("CREATE")
                    .fieldName(null)
                    .oldValue(null)
                    .newValue("Created project: " + safe(p.getTitle()))
                    .createdAt(LocalDateTime.now())
                    .build());

            return p;
        });

        log.info("Project created | id={} | media={} | traceId={}",
                saved.getId(), saved.getMedia() != null ? saved.getMedia().size() : 0, traceId);

        return saved;
    }

    /**
     * ======================================================
     * CREATE PROJECT (with file uploads)
     * ======================================================
     *
     * Speed improvements:
     * 1) Upload to S3 in parallel (cover + media files)
     * 2) Save to DB in one short transaction after uploads finish
     */
    public Project create(ProjectCreateRequest dto, MultipartFile cover, List<MultipartFile> mediaFiles) throws IOException {
        String traceId = MDC.get("traceId");
        int mediaCount = mediaFiles != null ? mediaFiles.size() : 0;

        log.info("Creating project with files | title={} | files={} | traceId={}",
                dto != null ? dto.getTitle() : null, mediaCount, traceId);

        validate(dto);

        // ---------- Uploads (OUTSIDE transaction) ----------
        String dtoCoverUrl = dto.getCoverUrl();
        List<UploadedMedia> uploadedMedia = new ArrayList<>();

        int threads = Math.min(8, Math.max(2, 1 + mediaCount));
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            CompletableFuture<String> coverFuture = CompletableFuture.completedFuture(dtoCoverUrl);

            if (cover != null && !cover.isEmpty()) {
                coverFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return s3Service.upload(
                                cover.getBytes(),
                                cover.getOriginalFilename(),
                                cover.getContentType(),
                                ProjectMediaType.IMAGE
                        );
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, pool);
            }

            List<CompletableFuture<UploadedMedia>> mediaFutures = new ArrayList<>();
            if (mediaFiles != null && !mediaFiles.isEmpty()) {
                int sortOrder = 0;
                for (MultipartFile file : mediaFiles) {
                    if (file == null || file.isEmpty()) continue;

                    final int so = sortOrder++;
                    mediaFutures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            ProjectMediaType mediaType = detectMediaType(file.getContentType());
                            String url = s3Service.upload(
                                    file.getBytes(),
                                    file.getOriginalFilename(),
                                    file.getContentType(),
                                    mediaType
                            );
                            return new UploadedMedia(mediaType, url, file.getOriginalFilename(), so, null);
                        } catch (IOException e) {
                            throw new CompletionException(e);
                        }
                    }, pool));
                }
            }

            // Wait for uploads
            String coverUrl = coverFuture.join();
            for (CompletableFuture<UploadedMedia> f : mediaFutures) {
                uploadedMedia.add(f.join());
            }

            // ---------- DB Save (SHORT transaction) ----------
            Project saved = transactionTemplate.execute(status -> {
                Project project = buildProject(dto);
                project.setCoverUrl(coverUrl);

                attachContents(project, dto.getContents());
                attachTags(project, dto.getTags());
                attachKeywords(project, dto.getKeywords());

                // Attach uploaded media
                for (UploadedMedia um : uploadedMedia) {
                    ProjectMedia media = ProjectMedia.builder()
                            .mediaType(um.mediaType())
                            .url(um.url())
                            .caption(um.caption())
                            .sortOrder(um.sortOrder())
                            .build();
                    project.addMedia(media);
                }

                // Also attach any media from DTO (pre-uploaded URLs)
                attachMediaFromDto(project, dto.getMedia());

                Project p = projectRepository.save(project);

                // ✅ Add Project Log (NO extra method)
                logRepository.save(ProjectLog.builder()
                        .project(p)
                        .action("CREATE")
                        .fieldName(null)
                        .oldValue(null)
                        .newValue("Created project with uploads: " + safe(p.getTitle()))
                        .createdAt(LocalDateTime.now())
                        .build());

                return p;
            });

            log.info("Project created with files | id={} | media={} | traceId={}",
                    saved.getId(), saved.getMedia() != null ? saved.getMedia().size() : 0, traceId);

            return saved;

        } catch (CompletionException ex) {
            Throwable root = ex.getCause() != null ? ex.getCause() : ex;
            if (root instanceof IOException io) throw io;
            throw ex;
        } finally {
            pool.shutdown();
        }
    }

    /**
     * ======================================================
     * UPDATE
     * ======================================================
     * - updates main fields + tags/contents/keywords + media (DTO urls)
     * - keeps transaction short
     */
    public Project update(Long projectId, ProjectCreateRequest dto) {
        String traceId = MDC.get("traceId");
        log.info("Updating project | id={} | traceId={}", projectId, traceId);

        if (projectId == null) {
            throw new BadRequestException("error.validation", "Project id is required");
        }
        validate(dto);

        Project updated = transactionTemplate.execute(status -> {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BadRequestException("project.not_found", "Project not found: " + projectId));

            // Track a simple change summary (fast)
            String oldTitle = project.getTitle();
            String oldCover = project.getCoverUrl();

            // Main fields
            project.setTitle(dto.getTitle().trim());
            project.setDescription(dto.getDescription());
            project.setProjectType(dto.getProjectType());
            project.setProjectDate(dto.getProjectDate());
            project.setLocation(dto.getLocation());
            project.setLanguage(dto.getLanguage());

            // Cover: update only if provided
            if (dto.getCoverUrl() != null && !dto.getCoverUrl().trim().isEmpty()) {
                project.setCoverUrl(dto.getCoverUrl().trim());
            }

            // Many-to-many sets: replace
            project.getContents().clear();
            project.getTags().clear();
            project.getKeywords().clear();

            attachContents(project, dto.getContents());
            attachTags(project, dto.getTags());
            attachKeywords(project, dto.getKeywords());

            // Media: replace DTO media (URLs)
            project.getMedia().clear();
            attachMediaFromDto(project, dto.getMedia());

            Project saved = projectRepository.save(project);

            // ✅ Add Project Log (NO extra method)
            StringBuilder changes = new StringBuilder("Updated project: ").append(safe(saved.getTitle()));
            if (oldTitle != null && !oldTitle.equals(saved.getTitle())) {
                changes.append(" | title: ").append(oldTitle).append(" -> ").append(saved.getTitle());
            }
            if (oldCover != null && saved.getCoverUrl() != null && !oldCover.equals(saved.getCoverUrl())) {
                changes.append(" | cover changed");
            }

            logRepository.save(ProjectLog.builder()
                    .project(saved)
                    .action("UPDATE")
                    .fieldName(null)
                    .oldValue(null)
                    .newValue(changes.toString())
                    .createdAt(LocalDateTime.now())
                    .build());

            return saved;
        });

        log.info("Project updated | id={} | traceId={}", updated.getId(), traceId);
        return updated;
    }

    /**
     * ======================================================
     * DELETE
     * ======================================================
     */
    public void delete(Long projectId) {
        String traceId = MDC.get("traceId");
        log.info("Deleting project | id={} | traceId={}", projectId, traceId);

        if (projectId == null) {
            throw new BadRequestException("error.validation", "Project id is required");
        }

        transactionTemplate.executeWithoutResult(status -> {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BadRequestException("project.not_found", "Project not found: " + projectId));

            // ✅ Add Project Log (NO extra method)
            logRepository.save(ProjectLog.builder()
                    .project(project)
                    .action("DELETE")
                    .fieldName(null)
                    .oldValue("Project existed")
                    .newValue("Deleted project: " + safe(project.getTitle()))
                    .createdAt(LocalDateTime.now())
                    .build());

            projectRepository.delete(project);
        });

        log.info("Project deleted | id={} | traceId={}", projectId, traceId);
    }

    /**
     * ======================================================
     * GET ALL
     * ======================================================
     */
    public List<Project> getAll() {
        return projectRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Page<Project> getAll(Pageable pageable) {
        return projectRepository.findAll(pageable);
    }

    /**
     * ======================================================
     * SEARCH (FAST, INDEX-FRIENDLY)
     * ======================================================
     * NOTE: For maximum speed on PostgreSQL:
     * - add indexes on normalized names in tag/content/keyword tables
     * - consider pg_trgm + GIN index for title/description "ILIKE" searches
     */
    public Page<Project> searchByName(String q, Pageable pageable) {
        String query = normalize(q);
        if (query.isEmpty()) return projectRepository.findAll(pageable);

        Specification<Project> spec = (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.like(cb.lower(root.get("title")), "%" + query + "%"));
            cq.distinct(true);
            return cb.and(preds.toArray(new Predicate[0]));
        };

        return projectRepository.findAll(spec, pageable);
    }

    public Page<Project> searchByTag(String tag, Pageable pageable) {
        String t = normalize(tag);
        if (t.isEmpty()) return projectRepository.findAll(pageable);

        Specification<Project> spec = (root, cq, cb) -> {
            Join<Project, ProjectTag> j = root.join("tags", JoinType.INNER);
            cq.distinct(true);
            return cb.equal(cb.lower(j.get("name")), t);
        };

        return projectRepository.findAll(spec, pageable);
    }

    public Page<Project> searchByContent(String content, Pageable pageable) {
        String c = normalize(content);
        if (c.isEmpty()) return projectRepository.findAll(pageable);

        Specification<Project> spec = (root, cq, cb) -> {
            Join<Project, ProjectContent> j = root.join("contents", JoinType.INNER);
            cq.distinct(true);
            return cb.equal(cb.lower(j.get("name")), c);
        };

        return projectRepository.findAll(spec, pageable);
    }

    public Page<Project> searchByKeyword(String keyword, Pageable pageable) {
        String k = normalize(keyword);
        if (k.isEmpty()) return projectRepository.findAll(pageable);

        Specification<Project> spec = (root, cq, cb) -> {
            Join<Project, ProjectKeyword> j = root.join("keywords", JoinType.INNER);
            cq.distinct(true);
            return cb.equal(cb.lower(j.get("name")), k);
        };

        return projectRepository.findAll(spec, pageable);
    }

    /**
     * Enhanced multi-filter search:
     * - name/title contains
     * - AND tag/content/keyword exact match lists (case-insensitive)
     *
     * This is usually faster than doing many separate DB calls.
     */
    public Page<Project> enhancedSearch(
            String nameContains,
            List<String> tags,
            List<String> contents,
            List<String> keywords,
            Pageable pageable
    ) {
        String nameQ = normalize(nameContains);

        Set<String> tagSet = normalizeSet(tags);
        Set<String> contentSet = normalizeSet(contents);
        Set<String> keywordSet = normalizeSet(keywords);

        Specification<Project> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!nameQ.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + nameQ + "%"));
            }

            if (!tagSet.isEmpty()) {
                Join<Project, ProjectTag> j = root.join("tags", JoinType.INNER);
                predicates.add(cb.lower(j.get("name")).in(tagSet));
            }

            if (!contentSet.isEmpty()) {
                Join<Project, ProjectContent> j = root.join("contents", JoinType.INNER);
                predicates.add(cb.lower(j.get("name")).in(contentSet));
            }

            if (!keywordSet.isEmpty()) {
                Join<Project, ProjectKeyword> j = root.join("keywords", JoinType.INNER);
                predicates.add(cb.lower(j.get("name")).in(keywordSet));
            }

            cq.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return projectRepository.findAll(spec, pageable);
    }

    // ══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS (kept from your style)
    // ══════════════════════════════════════════════════════════════

    private Project buildProject(ProjectCreateRequest dto) {
        return Project.builder()
                .coverUrl(dto.getCoverUrl())
                .title(dto.getTitle().trim())
                .description(dto.getDescription())
                .projectType(dto.getProjectType())
                .projectDate(dto.getProjectDate())
                .location(dto.getLocation())
                .language(dto.getLanguage())
                .build();
    }

    private void attachContents(Project project, List<String> names) {
        if (names == null || names.isEmpty()) return;

        names.forEach(name -> {
            if (isBlank(name)) return;

            ProjectContent content = contentRepository
                    .findByNameIgnoreCase(name.trim())
                    .orElseGet(() -> contentRepository.save(
                            ProjectContent.builder().name(name.trim()).build()
                    ));
            project.getContents().add(content);
        });
    }

    private void attachTags(Project project, List<String> names) {
        if (names == null || names.isEmpty()) return;

        names.forEach(name -> {
            if (isBlank(name)) return;

            ProjectTag tag = tagRepository
                    .findByNameIgnoreCase(name.trim())
                    .orElseGet(() -> tagRepository.save(
                            ProjectTag.builder().name(name.trim()).build()
                    ));
            project.getTags().add(tag);
        });
    }

    private void attachKeywords(Project project, List<String> names) {
        if (names == null || names.isEmpty()) return;

        names.forEach(name -> {
            if (isBlank(name)) return;

            ProjectKeyword keyword = keywordRepository
                    .findByNameIgnoreCase(name.trim())
                    .orElseGet(() -> keywordRepository.save(
                            ProjectKeyword.builder().name(name.trim()).build()
                    ));
            project.getKeywords().add(keyword);
        });
    }

    private void attachMediaFromDto(Project project, List<ProjectMediaCreateRequest> mediaList) {
        if (mediaList == null || mediaList.isEmpty()) return;

        mediaList.forEach(m -> {
            if (m == null) return;

            ProjectMedia media = ProjectMedia.builder()
                    .mediaType(ProjectMediaType.valueOf(m.getMediaType()))
                    .url(m.getUrl())
                    .caption(m.getCaption())
                    .sortOrder(m.getSortOrder() != null ? m.getSortOrder() : 0)
                    .build();
            project.addMedia(media);
        });
    }

    private ProjectMediaType detectMediaType(String contentType) {
        if (contentType == null) return ProjectMediaType.DOCUMENT;

        String type = contentType.toLowerCase();
        if (type.startsWith("image/")) return ProjectMediaType.IMAGE;
        if (type.startsWith("video/")) return ProjectMediaType.VIDEO;
        if (type.startsWith("audio/")) return ProjectMediaType.AUDIO;
        if (type.equals("application/pdf")) return ProjectMediaType.PDF;

        return ProjectMediaType.DOCUMENT;
    }

    private void validate(ProjectCreateRequest dto) {
        if (dto == null) {
            throw new BadRequestException("error.validation", "Request body is required");
        }
        if (isBlank(dto.getTitle())) {
            throw new BadRequestException("error.validation", "Title is required");
        }
        if (dto.getLanguage() == null) {
            throw new BadRequestException("error.validation", "Language is required");
        }

        if (dto.getMedia() != null) {
            for (ProjectMediaCreateRequest media : dto.getMedia()) {
                if (media == null) {
                    throw new BadRequestException("media.invalid", "Media item is null");
                }
                if (isBlank(media.getMediaType())) {
                    throw new BadRequestException("media.invalid", "Media type is required");
                }
                if (isBlank(media.getUrl()) && isBlank(media.getTextBody())) {
                    throw new BadRequestException("media.invalid", "Media url or textBody is required");
                }
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private Set<String> normalizeSet(List<String> list) {
        Set<String> out = new HashSet<>();
        if (list == null) return out;
        for (String s : list) {
            String n = normalize(s);
            if (!n.isEmpty()) out.add(n);
        }
        return out;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Small internal record to store uploaded media result.
     */
    private record UploadedMedia(ProjectMediaType mediaType, String url, String caption, int sortOrder, String textBody) {}
}
