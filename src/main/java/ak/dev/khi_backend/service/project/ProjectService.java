package ak.dev.khi_backend.service.project;

import ak.dev.khi_backend.dto.project.ProjectCreateRequest;
import ak.dev.khi_backend.dto.project.ProjectMediaCreateRequest;
import ak.dev.khi_backend.dto.project.ProjectMediaResponse;
import ak.dev.khi_backend.dto.project.ProjectResponse;
import ak.dev.khi_backend.enums.project.ProjectMediaType;
import ak.dev.khi_backend.exceptions.BadRequestException;
import ak.dev.khi_backend.model.project.Project;
import ak.dev.khi_backend.model.project.ProjectContent;
import ak.dev.khi_backend.model.project.ProjectKeyword;
import ak.dev.khi_backend.model.project.ProjectLog;
import ak.dev.khi_backend.model.project.ProjectMedia;
import ak.dev.khi_backend.model.project.ProjectTag;
import ak.dev.khi_backend.repository.project.ProjectContentRepository;
import ak.dev.khi_backend.repository.project.ProjectKeywordRepository;
import ak.dev.khi_backend.repository.project.ProjectLogRepository;
import ak.dev.khi_backend.repository.project.ProjectMediaRepository;
import ak.dev.khi_backend.repository.project.ProjectRepository;
import ak.dev.khi_backend.repository.project.ProjectTagRepository;
import ak.dev.khi_backend.service.S3Service;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Service layer for Project management
 *
 * Responsibilities:
 * - CRUD operations for projects
 * - File upload handling (S3)
 * - Search and filtering
 * - Entity to DTO conversion
 * - Transaction management
 * - Audit logging
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    // ══════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ══════════════════════════════════════════════════════════════

    private final ProjectRepository projectRepository;
    private final ProjectContentRepository contentRepository;
    private final ProjectTagRepository tagRepository;
    private final ProjectKeywordRepository keywordRepository;
    private final ProjectMediaRepository mediaRepository;
    private final ProjectLogRepository logRepository;
    private final S3Service s3Service;
    private final TransactionTemplate transactionTemplate;

    // ══════════════════════════════════════════════════════════════
    // CREATE OPERATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Create project (JSON only - URLs already uploaded)
     *
     * @param dto Project creation request
     * @return Created project entity
     */
    public Project create(ProjectCreateRequest dto) {
        String traceId = MDC.get("traceId");
        log.info("Creating project | title={} | traceId={}",
                dto != null ? dto.getTitle() : null, traceId);

        validate(dto);

        // Keep DB transaction short
        Project saved = transactionTemplate.execute(status -> {
            Project project = buildProject(dto);
            attachContents(project, dto.getContents());
            attachTags(project, dto.getTags());
            attachKeywords(project, dto.getKeywords());
            attachMediaFromDto(project, dto.getMedia());

            Project p = projectRepository.save(project);

            // Audit log
            createAuditLog(p, "CREATE",
                    "Created project: " + safe(p.getTitle()));

            return p;
        });

        log.info("Project created | id={} | media={} | traceId={}",
                saved.getId(),
                saved.getMedia() != null ? saved.getMedia().size() : 0,
                traceId);

        return saved;
    }

    /**
     * Create project with file uploads
     *
     * Performance optimizations:
     * 1. Upload to S3 in parallel (cover + media files)
     * 2. Save to DB in one short transaction after uploads finish
     *
     * @param dto Project creation request
     * @param cover Cover image file (optional)
     * @param mediaFiles List of media files (optional)
     * @return Created project entity
     * @throws IOException If file upload fails
     */
    public Project create(
            ProjectCreateRequest dto,
            MultipartFile cover,
            List<MultipartFile> mediaFiles
    ) throws IOException {
        String traceId = MDC.get("traceId");
        int mediaCount = mediaFiles != null ? mediaFiles.size() : 0;

        log.info("Creating project with files | title={} | files={} | traceId={}",
                dto != null ? dto.getTitle() : null, mediaCount, traceId);

        validate(dto);

        // ─────────────────────────────────────────────────────────
        // STEP 1: Upload files to S3 (OUTSIDE transaction)
        // ─────────────────────────────────────────────────────────

        String dtoCoverUrl = dto.getCoverUrl();
        List<UploadedMedia> uploadedMedia = new ArrayList<>();

        // Create thread pool for parallel uploads
        int threads = Math.min(8, Math.max(2, 1 + mediaCount));
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            // Upload cover image (if provided)
            CompletableFuture<String> coverFuture =
                    CompletableFuture.completedFuture(dtoCoverUrl);

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

            // Upload media files in parallel
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
                            return new UploadedMedia(
                                    mediaType,
                                    url,
                                    file.getOriginalFilename(),
                                    so,
                                    null
                            );
                        } catch (IOException e) {
                            throw new CompletionException(e);
                        }
                    }, pool));
                }
            }

            // Wait for all uploads to complete
            String coverUrl = coverFuture.join();
            for (CompletableFuture<UploadedMedia> f : mediaFutures) {
                uploadedMedia.add(f.join());
            }

            // ─────────────────────────────────────────────────────────
            // STEP 2: Save to database (SHORT transaction)
            // ─────────────────────────────────────────────────────────

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

                // Audit log
                createAuditLog(p, "CREATE",
                        "Created project with uploads: " + safe(p.getTitle()));

                return p;
            });

            log.info("Project created with files | id={} | media={} | traceId={}",
                    saved.getId(),
                    saved.getMedia() != null ? saved.getMedia().size() : 0,
                    traceId);

            return saved;

        } catch (CompletionException ex) {
            Throwable root = ex.getCause() != null ? ex.getCause() : ex;
            if (root instanceof IOException io) throw io;
            throw ex;
        } finally {
            pool.shutdown();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // UPDATE OPERATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Update existing project
     *
     * @param projectId Project ID
     * @param dto Updated project data
     * @return Updated project entity
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
                    .orElseThrow(() -> new BadRequestException(
                            "project.not_found",
                            "Project not found: " + projectId
                    ));

            // Track changes for audit
            String oldTitle = project.getTitle();
            String oldCover = project.getCoverUrl();

            // Update main fields
            project.setTitle(dto.getTitle().trim());
            project.setDescription(dto.getDescription());
            project.setProjectType(dto.getProjectType());
            project.setProjectDate(dto.getProjectDate());
            project.setLocation(dto.getLocation());
            project.setLanguage(dto.getLanguage());

            // Update cover only if provided
            if (dto.getCoverUrl() != null && !dto.getCoverUrl().trim().isEmpty()) {
                project.setCoverUrl(dto.getCoverUrl().trim());
            }

            // Replace many-to-many relationships
            project.getContents().clear();
            project.getTags().clear();
            project.getKeywords().clear();

            attachContents(project, dto.getContents());
            attachTags(project, dto.getTags());
            attachKeywords(project, dto.getKeywords());

            // Replace media (DTO URLs)
            project.getMedia().clear();
            attachMediaFromDto(project, dto.getMedia());

            Project saved = projectRepository.save(project);

            // Build audit log message
            StringBuilder changes = new StringBuilder("Updated project: ")
                    .append(safe(saved.getTitle()));

            if (oldTitle != null && !oldTitle.equals(saved.getTitle())) {
                changes.append(" | title: ")
                        .append(oldTitle)
                        .append(" -> ")
                        .append(saved.getTitle());
            }
            if (oldCover != null && saved.getCoverUrl() != null
                    && !oldCover.equals(saved.getCoverUrl())) {
                changes.append(" | cover changed");
            }

            createAuditLog(saved, "UPDATE", changes.toString());

            return saved;
        });

        log.info("Project updated | id={} | traceId={}", updated.getId(), traceId);
        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // DELETE OPERATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Delete project by ID
     *
     * @param projectId Project ID
     */
    public void delete(Long projectId) {
        String traceId = MDC.get("traceId");
        log.info("Deleting project | id={} | traceId={}", projectId, traceId);

        if (projectId == null) {
            throw new BadRequestException("error.validation", "Project id is required");
        }

        transactionTemplate.executeWithoutResult(status -> {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BadRequestException(
                            "project.not_found",
                            "Project not found: " + projectId
                    ));

            // Audit log before deletion
            createAuditLog(project, "DELETE",
                    "Deleted project: " + safe(project.getTitle()));

            projectRepository.delete(project);
        });

        log.info("Project deleted | id={} | traceId={}", projectId, traceId);
    }

    // ══════════════════════════════════════════════════════════════
    // READ OPERATIONS - GET ALL
    // ══════════════════════════════════════════════════════════════

    /**
     * Get all projects (sorted, no pagination)
     *
     * @return List of all projects
     */
    public List<Project> getAll() {
        return projectRepository.findAll(
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    /**
     * Get all projects (paginated)
     *
     * @param pageable Pagination parameters
     * @return Page of projects (entities)
     */
    public Page<Project> getAll(Pageable pageable) {
        return projectRepository.findAll(pageable);
    }

    /**
     * ✅ Get all projects as DTOs (SAFE for API responses)
     *
     * This method prevents LazyInitializationException by:
     * 1. Running in a read-only transaction
     * 2. Eagerly loading lazy collections via toResponse()
     * 3. Returning DTOs instead of entities
     *
     * @param pageable Pagination parameters
     * @return Page of project DTOs
     */
    @Transactional(readOnly = true)
    public Page<ProjectResponse> getAllResponse(Pageable pageable) {
        Page<Project> page = projectRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    // ══════════════════════════════════════════════════════════════
    // SEARCH OPERATIONS - SINGLE FILTER (ENTITY RESULTS)
    // ══════════════════════════════════════════════════════════════

    /**
     * Search by name/title (contains, case-insensitive)
     *
     * @param q Search query
     * @param pageable Pagination
     * @return Page of matching projects (entities)
     */
    public Page<Project> searchByName(String q, Pageable pageable) {
        String query = normalize(q);
        if (query.isEmpty()) return projectRepository.findAll(pageable);
        return projectRepository.searchByTitle(query, pageable);
    }

    /**
     * Search by tag (exact match, case-insensitive)
     *
     * @param tag Tag name
     * @param pageable Pagination
     * @return Page of matching projects (entities)
     */
    public Page<Project> searchByTag(String tag, Pageable pageable) {
        String t = normalize(tag);
        if (t.isEmpty()) return projectRepository.findAll(pageable);
        return projectRepository.searchByTag(t, pageable);
    }

    /**
     * Search by content type (exact match, case-insensitive)
     *
     * @param content Content type name
     * @param pageable Pagination
     * @return Page of matching projects (entities)
     */
    public Page<Project> searchByContent(String content, Pageable pageable) {
        String c = normalize(content);
        if (c.isEmpty()) return projectRepository.findAll(pageable);
        return projectRepository.searchByContent(c, pageable);
    }

    /**
     * Search by keyword (exact match, case-insensitive)
     *
     * @param keyword Keyword
     * @param pageable Pagination
     * @return Page of matching projects (entities)
     */
    public Page<Project> searchByKeyword(String keyword, Pageable pageable) {
        String k = normalize(keyword);
        if (k.isEmpty()) return projectRepository.findAll(pageable);
        return projectRepository.searchByKeyword(k, pageable);
    }

    // ══════════════════════════════════════════════════════════════
    // SEARCH OPERATIONS - SINGLE FILTER (DTO RESULTS) ✅
    // ══════════════════════════════════════════════════════════════

    /**
     * ✅ Search by name - Returns DTOs (SAFE for API)
     */
    @Transactional(readOnly = true)
    public Page<ProjectResponse> searchByNameResponse(String q, Pageable pageable) {
        Page<Project> page = searchByName(q, pageable);
        return page.map(this::toResponse);
    }

    /**
     * ✅ Search by tag - Returns DTOs (SAFE for API)
     */
    @Transactional(readOnly = true)
    public Page<ProjectResponse> searchByTagResponse(String tag, Pageable pageable) {
        Page<Project> page = searchByTag(tag, pageable);
        return page.map(this::toResponse);
    }

    /**
     * ✅ Search by content - Returns DTOs (SAFE for API)
     */
    @Transactional(readOnly = true)
    public Page<ProjectResponse> searchByContentResponse(String content, Pageable pageable) {
        Page<Project> page = searchByContent(content, pageable);
        return page.map(this::toResponse);
    }

    /**
     * ✅ Search by keyword - Returns DTOs (SAFE for API)
     */
    @Transactional(readOnly = true)
    public Page<ProjectResponse> searchByKeywordResponse(String keyword, Pageable pageable) {
        Page<Project> page = searchByKeyword(keyword, pageable);
        return page.map(this::toResponse);
    }

    // ══════════════════════════════════════════════════════════════
    // SEARCH OPERATIONS - MULTI FILTER (ENHANCED)
    // ══════════════════════════════════════════════════════════════

    /**
     * Enhanced multi-filter search (entities)
     *
     * Filters:
     * - nameContains: Title contains (case-insensitive)
     * - tags: Exact match list (case-insensitive)
     * - contents: Exact match list (case-insensitive)
     * - keywords: Exact match list (case-insensitive)
     *
     * @param nameContains Name/title search query
     * @param tags List of tags
     * @param contents List of content types
     * @param keywords List of keywords
     * @param pageable Pagination
     * @return Page of matching projects (entities)
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

            // Name filter (contains)
            if (!nameQ.isEmpty()) {
                predicates.add(cb.like(
                        cb.lower(root.get("title")),
                        "%" + nameQ + "%"
                ));
            }

            // Tag filter (exact match, multiple)
            if (!tagSet.isEmpty()) {
                Join<Project, ProjectTag> j = root.join("tags", JoinType.INNER);
                predicates.add(cb.lower(j.get("name")).in(tagSet));
            }

            // Content filter (exact match, multiple)
            if (!contentSet.isEmpty()) {
                Join<Project, ProjectContent> j = root.join("contents", JoinType.INNER);
                predicates.add(cb.lower(j.get("name")).in(contentSet));
            }

            // Keyword filter (exact match, multiple)
            if (!keywordSet.isEmpty()) {
                Join<Project, ProjectKeyword> j = root.join("keywords", JoinType.INNER);
                predicates.add(cb.lower(j.get("name")).in(keywordSet));
            }

            cq.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return projectRepository.findAll(spec, pageable);
    }

    /**
     * ✅ Enhanced search - Returns DTOs (SAFE for API)
     *
     * This is the method controllers should use to avoid LazyInitializationException.
     *
     * @param nameContains Name/title search query
     * @param tags List of tags
     * @param contents List of content types
     * @param keywords List of keywords
     * @param pageable Pagination
     * @return Page of project DTOs
     */
    @Transactional(readOnly = true)
    public Page<ProjectResponse> enhancedSearchResponse(
            String nameContains,
            List<String> tags,
            List<String> contents,
            List<String> keywords,
            Pageable pageable
    ) {
        Page<Project> page = enhancedSearch(nameContains, tags, contents, keywords, pageable);
        return page.map(this::toResponse);
    }

    // ══════════════════════════════════════════════════════════════
    // DTO CONVERSION
    // ══════════════════════════════════════════════════════════════

    /**
     * Convert Project entity to ProjectResponse DTO
     *
     * This method MUST run inside a transaction to access lazy-loaded collections.
     * It eagerly loads all collections to prevent LazyInitializationException.
     *
     * @param p Project entity
     * @return ProjectResponse DTO
     */
    private ProjectResponse toResponse(Project p) {
        // Contents (joined as comma-separated string)
        String contentJoined = null;
        if (p.getContents() != null && !p.getContents().isEmpty()) {
            contentJoined = p.getContents().stream()
                    .map(ProjectContent::getName)
                    .filter(x -> x != null && !x.isBlank())
                    .distinct()
                    .sorted()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(null);
        }

        // Tags (list of strings)
        List<String> tags = (p.getTags() == null) ? List.of() :
                p.getTags().stream()
                        .map(ProjectTag::getName)
                        .filter(x -> x != null && !x.isBlank())
                        .distinct()
                        .sorted()
                        .toList();

        // Keywords (list of strings)
        List<String> keywords = (p.getKeywords() == null) ? List.of() :
                p.getKeywords().stream()
                        .map(ProjectKeyword::getName)
                        .filter(x -> x != null && !x.isBlank())
                        .distinct()
                        .sorted()
                        .toList();

        // Media (list of media DTOs, sorted by sortOrder)
        List<ProjectMediaResponse> media = (p.getMedia() == null) ? List.of() :
                p.getMedia().stream()
                        .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                        .map(m -> ProjectMediaResponse.builder()
                                .mediaType(m.getMediaType() != null ? m.getMediaType().name() : null)
                                .url(m.getUrl())
                                .caption(m.getCaption())
                                .sortOrder(m.getSortOrder())
                                .build())
                        .toList();

        // Date as string
        String dateStr = p.getProjectDate() != null ? p.getProjectDate().toString() : null;

        return ProjectResponse.builder()
                .id(p.getId())
                .cover(p.getCoverUrl())
                .title(p.getTitle())
                .description(p.getDescription())
                .projectType(p.getProjectType() != null ? p.getProjectType().toString() : null)
                .content(contentJoined)
                .tags(tags)
                .keywords(keywords)
                .date(dateStr)
                .location(p.getLocation())
                .language(p.getLanguage() != null ? p.getLanguage().name() : null)
                .result(null)
                .createdAt(
                        p.getCreatedAt() == null
                                ? null
                                : p.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                )
                .media(media)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS - PROJECT BUILDING
    // ══════════════════════════════════════════════════════════════

    /**
     * Build Project entity from DTO (without relationships)
     */
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

    /**
     * Attach content types to project (find or create)
     */
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

    /**
     * Attach tags to project (find or create)
     */
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

    /**
     * Attach keywords to project (find or create)
     */
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

    /**
     * Attach media from DTO (pre-uploaded URLs)
     */
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

    // ══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS - MEDIA TYPE DETECTION
    // ══════════════════════════════════════════════════════════════

    /**
     * Detect media type from content type (MIME type)
     */
    private ProjectMediaType detectMediaType(String contentType) {
        if (contentType == null) return ProjectMediaType.DOCUMENT;

        String type = contentType.toLowerCase();
        if (type.startsWith("image/")) return ProjectMediaType.IMAGE;
        if (type.startsWith("video/")) return ProjectMediaType.VIDEO;
        if (type.startsWith("audio/")) return ProjectMediaType.AUDIO;
        if (type.equals("application/pdf")) return ProjectMediaType.PDF;

        return ProjectMediaType.DOCUMENT;
    }

    // ══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS - VALIDATION
    // ══════════════════════════════════════════════════════════════

    /**
     * Validate project creation request
     */
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
                    throw new BadRequestException("media.invalid",
                            "Media url or textBody is required");
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS - AUDIT LOGGING
    // ══════════════════════════════════════════════════════════════

    /**
     * Create audit log entry
     */
    private void createAuditLog(Project project, String action, String message) {
        logRepository.save(ProjectLog.builder()
                .project(project)
                .action(action)
                .fieldName(null)
                .oldValue(null)
                .newValue(message)
                .createdAt(LocalDateTime.now())
                .build());
    }

    // ══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS - STRING UTILITIES
    // ══════════════════════════════════════════════════════════════

    /**
     * Check if string is blank (null or empty after trim)
     */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Normalize string (trim and lowercase)
     */
    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    /**
     * Normalize list of strings to set (trim, lowercase, remove blanks)
     */
    private Set<String> normalizeSet(List<String> list) {
        Set<String> out = new HashSet<>();
        if (list == null) return out;
        for (String s : list) {
            String n = normalize(s);
            if (!n.isEmpty()) out.add(n);
        }
        return out;
    }

    /**
     * Safe string (return empty string if null)
     */
    private String safe(String s) {
        return s == null ? "" : s;
    }

    // ══════════════════════════════════════════════════════════════
    // INTERNAL DATA CLASSES
    // ══════════════════════════════════════════════════════════════

    /**
     * Internal record to store uploaded media result
     */
    private record UploadedMedia(
            ProjectMediaType mediaType,
            String url,
            String caption,
            int sortOrder,
            String textBody
    ) {}
}