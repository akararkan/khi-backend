package ak.dev.khi_backend.khi_app.service.project;

import ak.dev.khi_backend.khi_app.dto.project.ProjectCreateRequest;
import ak.dev.khi_backend.khi_app.dto.project.ProjectMediaCreateRequest;
import ak.dev.khi_backend.khi_app.dto.project.ProjectMediaResponse;
import ak.dev.khi_backend.khi_app.dto.project.ProjectResponse;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.project.ProjectMediaType;
import ak.dev.khi_backend.khi_app.exceptions.*;
import ak.dev.khi_backend.khi_app.model.project.*;
import ak.dev.khi_backend.khi_app.repository.project.*;
import ak.dev.khi_backend.khi_app.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectContentRepository projectContentRepository;
    private final ProjectTagRepository projectTagRepository;
    private final ProjectKeywordRepository projectKeywordRepository;
    private final ProjectLogRepository projectLogRepository;
    private final ProjectMediaRepository projectMediaRepository;
    private final S3Service s3Service;
    private final PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager em;

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    // ===========================
    // CREATE (JSON only)
    // ===========================
    public Project create(ProjectCreateRequest dto) {
        String traceId = traceId();
        log.info("Creating project | langs={} | traceId={}", dto != null ? dto.getContentLanguages() : null, traceId);

        validate(dto);

        try {
            Project saved = transactionTemplate().execute(status -> {
                Project project = buildProject(dto);

                attachAllContents(project, dto);
                attachAllTags(project, dto);
                attachAllKeywords(project, dto);
                attachMediaFromDto(project, dto.getMedia());

                Project p = projectRepository.save(project);
                createAuditLog(p, "CREATE", "Created project: " + safeTitle(p));
                return p;
            });

            log.info("Project created | id={} | traceId={}", saved.getId(), traceId);
            return saved;

        } catch (AppException ex) {
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("project.conflict", Map.of("traceId", safe(traceId)));
        } catch (Exception ex) {
            log.error("Unexpected error creating project | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "create", "traceId", safe(traceId)));
        }
    }

    // ===========================
    // CREATE (with files)
    // ===========================
    public Project create(ProjectCreateRequest dto, MultipartFile cover, List<MultipartFile> mediaFiles) throws IOException {
        String traceId = traceId();
        int mediaCount = mediaFiles != null ? mediaFiles.size() : 0;

        log.info("Creating project with files | mediaFiles={} | langs={} | traceId={}",
                mediaCount, dto != null ? dto.getContentLanguages() : null, traceId);

        validate(dto);

        String dtoCoverUrl = dto.getCoverUrl();
        List<UploadedMedia> uploadedMedia = new ArrayList<>();

        int threads = Math.min(8, Math.max(2, 1 + mediaCount));
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            // ── parallel S3 uploads ──────────────────────────────
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

                    final int order = sortOrder++;
                    mediaFutures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            ProjectMediaType type = detectMediaType(file);
                            String url = s3Service.upload(
                                    file.getBytes(),
                                    file.getOriginalFilename(),
                                    file.getContentType(),
                                    type
                            );
                            return new UploadedMedia(type, url, null, order);
                        } catch (IOException e) {
                            throw new CompletionException(e);
                        }
                    }, pool));
                }
            }

            CompletableFuture<Void> allMedia = CompletableFuture.allOf(mediaFutures.toArray(new CompletableFuture[0]));

            try {
                allMedia.join();
                for (CompletableFuture<UploadedMedia> f : mediaFutures) {
                    uploadedMedia.add(f.join());
                }
            } catch (CompletionException ex) {
                Throwable root = ex.getCause() != null ? ex.getCause() : ex;
                if (root instanceof IOException io) throw io;
                throw ex;
            }

            String coverUrl = coverFuture.join();

            // ── persist project inside transaction ───────────────
            Project saved = transactionTemplate().execute(status -> {
                Project project = buildProject(dto);
                project.setCoverUrl(coverUrl);

                attachAllContents(project, dto);
                attachAllTags(project, dto);
                attachAllKeywords(project, dto);

                // uploaded media files (optional)
                for (UploadedMedia um : uploadedMedia) {
                    project.addMedia(ProjectMedia.builder()
                            .mediaType(um.mediaType())
                            .url(um.url())
                            .caption(um.caption())
                            .sortOrder(um.sortOrder())
                            .build());
                }

                // media links/embed/externalUrl (optional)
                attachMediaFromDto(project, dto.getMedia());

                Project p = projectRepository.save(project);
                createAuditLog(p, "CREATE", "Created project with uploads: " + safeTitle(p));
                return p;
            });

            log.info("Project created with files | id={} | traceId={}", saved.getId(), traceId);
            return saved;

        } catch (AppException ex) {
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("project.conflict", Map.of("traceId", safe(traceId)));
        } catch (Exception ex) {
            log.error("Unexpected error creating project with files | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "createWithFiles", "traceId", safe(traceId)));
        } finally {
            pool.shutdownNow();
        }
    }

    // ===========================
    // UPDATE (JSON only)
    // ===========================
    public Project update(Long projectId, ProjectCreateRequest dto) {
        String traceId = traceId();
        log.info("Updating project | id={} | traceId={}", projectId, traceId);

        validate(dto);

        try {
            Project saved = transactionTemplate().execute(status -> {
                Project project = projectRepository.findById(projectId)
                        .orElseThrow(() -> new NotFoundException("project.not_found", Map.of("id", safe(projectId))));

                applyBaseFields(project, dto);

                // Clear & re-attach relations safely
                project.getContentsCkb().clear();
                project.getContentsKmr().clear();
                project.getTagsCkb().clear();
                project.getTagsKmr().clear();
                project.getKeywordsCkb().clear();
                project.getKeywordsKmr().clear();

                // replace media only if dto contains media array (optional)
                if (dto.getMedia() != null) {
                    project.getMedia().clear();
                }

                attachAllContents(project, dto);
                attachAllTags(project, dto);
                attachAllKeywords(project, dto);
                attachMediaFromDto(project, dto.getMedia());

                Project savedProject = projectRepository.save(project);
                createAuditLog(savedProject, "UPDATE", "Updated project: " + safeTitle(savedProject));
                return savedProject;
            });

            log.info("Project updated | id={} | traceId={}", saved.getId(), traceId);
            return saved;

        } catch (AppException ex) {
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("project.conflict", Map.of("traceId", safe(traceId)));
        } catch (Exception ex) {
            log.error("Unexpected error updating project | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "update", "traceId", safe(traceId)));
        }
    }

    // ===========================
    // UPDATE (with files)
    // ===========================
    public Project updateWithFiles(Long projectId, ProjectCreateRequest dto, MultipartFile cover, List<MultipartFile> mediaFiles) throws IOException {
        String traceId = traceId();
        int mediaCount = mediaFiles != null ? mediaFiles.size() : 0;

        log.info("Updating project with files | id={} | mediaFiles={} | traceId={}", projectId, mediaCount, traceId);

        validate(dto);

        List<UploadedMedia> uploadedMedia = new ArrayList<>();
        int threads = Math.min(8, Math.max(2, 1 + mediaCount));
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            CompletableFuture<String> coverFuture = CompletableFuture.completedFuture(dto.getCoverUrl());

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

                    final int order = sortOrder++;
                    mediaFutures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            ProjectMediaType type = detectMediaType(file);
                            String url = s3Service.upload(
                                    file.getBytes(),
                                    file.getOriginalFilename(),
                                    file.getContentType(),
                                    type
                            );
                            return new UploadedMedia(type, url, null, order);
                        } catch (IOException e) {
                            throw new CompletionException(e);
                        }
                    }, pool));
                }
            }

            CompletableFuture<Void> allMedia = CompletableFuture.allOf(mediaFutures.toArray(new CompletableFuture[0]));
            allMedia.join();
            for (CompletableFuture<UploadedMedia> f : mediaFutures) {
                uploadedMedia.add(f.join());
            }

            String coverUrl = coverFuture.join();

            Project saved = transactionTemplate().execute(status -> {
                Project project = projectRepository.findById(projectId)
                        .orElseThrow(() -> new NotFoundException("project.not_found", Map.of("id", safe(projectId))));

                applyBaseFields(project, dto);
                project.setCoverUrl(coverUrl);

                // Clear & re-attach relations
                project.getContentsCkb().clear();
                project.getContentsKmr().clear();
                project.getTagsCkb().clear();
                project.getTagsKmr().clear();
                project.getKeywordsCkb().clear();
                project.getKeywordsKmr().clear();

                // replace media only if dto contains media array (optional)
                if (dto.getMedia() != null) {
                    project.getMedia().clear();
                }

                attachAllContents(project, dto);
                attachAllTags(project, dto);
                attachAllKeywords(project, dto);

                // append uploaded media files
                for (UploadedMedia um : uploadedMedia) {
                    project.addMedia(ProjectMedia.builder()
                            .mediaType(um.mediaType())
                            .url(um.url())
                            .caption(um.caption())
                            .sortOrder(um.sortOrder())
                            .build());
                }

                // add dto media (links/embed/externalUrl) optional
                attachMediaFromDto(project, dto.getMedia());

                Project savedProject = projectRepository.save(project);
                createAuditLog(savedProject, "UPDATE", "Updated project with uploads: " + safeTitle(savedProject));
                return savedProject;
            });

            log.info("Project updated with files | id={} | traceId={}", saved.getId(), traceId);
            return saved;

        } catch (AppException ex) {
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("project.conflict", Map.of("traceId", safe(traceId)));
        } catch (Exception ex) {
            log.error("Unexpected error updating project with files | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "updateWithFiles", "traceId", safe(traceId)));
        } finally {
            pool.shutdownNow();
        }
    }

    // ===========================
    // DELETE
    // ===========================
    public void delete(Long projectId) {
        String traceId = traceId();
        log.info("Deleting project | id={} | traceId={}", projectId, traceId);

        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new NotFoundException("project.not_found", Map.of("id", safe(projectId))));

            projectRepository.delete(project);
            createAuditLog(project, "DELETE", "Deleted project: " + safeTitle(project));
            log.info("Project deleted | id={} | traceId={}", projectId, traceId);

        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error deleting project | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "delete", "traceId", safe(traceId)));
        }
    }

    // ============================================================
    // RESPONSE HELPERS
    // ============================================================

    public Page<ProjectResponse> getAllResponse(int page, int size) {
        Page<Project> projects = projectRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return projects.map(this::toResponse);
    }

    public Page<ProjectResponse> searchByTagResponse(String tag, int page, int size) {
        if (isBlank(tag)) throw new BadRequestException("tag.required", Map.of());
        Page<Project> projects = projectRepository.searchByTag(tag, PageRequest.of(page, size));
        return projects.map(this::toResponse);
    }

    public Page<ProjectResponse> searchByKeywordResponse(String keyword, int page, int size) {
        if (isBlank(keyword)) throw new BadRequestException("keyword.required", Map.of());
        Page<Project> projects = projectRepository.searchByKeyword(keyword, PageRequest.of(page, size));
        return projects.map(this::toResponse);
    }

    private ProjectResponse toResponse(Project project) {
        ProjectResponse.ProjectContentBlockDto ckb = null;
        if (project.getCkbContent() != null) {
            ckb = ProjectResponse.ProjectContentBlockDto.builder()
                    .title(project.getCkbContent().getTitle())
                    .description(project.getCkbContent().getDescription())
                    .location(project.getCkbContent().getLocation())
                    .build();
        }

        ProjectResponse.ProjectContentBlockDto kmr = null;
        if (project.getKmrContent() != null) {
            kmr = ProjectResponse.ProjectContentBlockDto.builder()
                    .title(project.getKmrContent().getTitle())
                    .description(project.getKmrContent().getDescription())
                    .location(project.getKmrContent().getLocation())
                    .build();
        }

        List<String> contentsCkb = new ArrayList<>();
        if (project.getContentsCkb() != null) {
            for (ProjectContent c : project.getContentsCkb()) {
                if (c != null && !isBlank(c.getName())) contentsCkb.add(c.getName());
            }
        }

        List<String> contentsKmr = new ArrayList<>();
        if (project.getContentsKmr() != null) {
            for (ProjectContent c : project.getContentsKmr()) {
                if (c != null && !isBlank(c.getName())) contentsKmr.add(c.getName());
            }
        }

        List<String> tagsCkb = new ArrayList<>();
        if (project.getTagsCkb() != null) {
            for (ProjectTag t : project.getTagsCkb()) {
                if (t != null && !isBlank(t.getName())) tagsCkb.add(t.getName());
            }
        }

        List<String> tagsKmr = new ArrayList<>();
        if (project.getTagsKmr() != null) {
            for (ProjectTag t : project.getTagsKmr()) {
                if (t != null && !isBlank(t.getName())) tagsKmr.add(t.getName());
            }
        }

        List<String> keywordsCkb = new ArrayList<>();
        if (project.getKeywordsCkb() != null) {
            for (ProjectKeyword k : project.getKeywordsCkb()) {
                if (k != null && !isBlank(k.getName())) keywordsCkb.add(k.getName());
            }
        }

        List<String> keywordsKmr = new ArrayList<>();
        if (project.getKeywordsKmr() != null) {
            for (ProjectKeyword k : project.getKeywordsKmr()) {
                if (k != null && !isBlank(k.getName())) keywordsKmr.add(k.getName());
            }
        }

        List<ProjectMediaResponse> media = new ArrayList<>();
        if (project.getMedia() != null && !project.getMedia().isEmpty()) {
            List<ProjectMedia> sorted = new ArrayList<>(project.getMedia());
            sorted.sort(Comparator.comparingInt(ProjectMedia::getSortOrder).thenComparing(ProjectMedia::getId));

            media = new ArrayList<>(sorted.size());
            for (ProjectMedia m : sorted) {
                media.add(ProjectMediaResponse.builder()
                        .id(m.getId())
                        .mediaType(m.getMediaType() != null ? m.getMediaType().name() : null)
                        .url(m.getUrl())
                        .externalUrl(m.getExternalUrl())
                        .embedUrl(m.getEmbedUrl())
                        .caption(m.getCaption())
                        .sortOrder(m.getSortOrder())
                        .textBody(null)
                        .build());
            }
        }

        Instant createdAt =
                project.getCreatedAt() != null ? project.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null;

        return ProjectResponse.builder()
                .id(project.getId())
                .coverUrl(project.getCoverUrl())
                .projectType(project.getProjectType())
                .projectDate(project.getProjectDate())
                .contentLanguages(project.getContentLanguages())
                .ckbContent(ckb)
                .kmrContent(kmr)
                .contentsCkb(contentsCkb)
                .contentsKmr(contentsKmr)
                .tagsCkb(tagsCkb)
                .tagsKmr(tagsKmr)
                .keywordsCkb(keywordsCkb)
                .keywordsKmr(keywordsKmr)
                .createdAt(createdAt)
                .media(media)
                .build();
    }

    // ============================================================
    // MEDIA DTO ATTACH (supports url OR externalUrl OR embedUrl)
    // ============================================================
    private void attachMediaFromDto(Project project, List<ProjectMediaCreateRequest> mediaList) {
        if (mediaList == null || mediaList.isEmpty()) return;

        for (ProjectMediaCreateRequest m : mediaList) {
            if (m == null) continue;

            ProjectMediaType type;
            try {
                type = ProjectMediaType.valueOf(m.getMediaType());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("media.type_invalid", Map.of("mediaType", safe(m.getMediaType())));
            }

            boolean hasFileUrl = !isBlank(m.getUrl());
            boolean hasExternal = !isBlank(m.getExternalUrl());
            boolean hasEmbed = !isBlank(m.getEmbedUrl());
            boolean hasText = !isBlank(m.getTextBody());

            // AUDIO/VIDEO: require at least one of (url, externalUrl, embedUrl)
            if (type == ProjectMediaType.AUDIO || type == ProjectMediaType.VIDEO) {
                if (!hasFileUrl && !hasExternal && !hasEmbed) {
                    throw new BadRequestException(
                            "media.audio_video_requires_url_or_link",
                            Map.of("mediaType", type.name())
                    );
                }
            } else {
                // others: require (url OR textBody)
                if (!hasFileUrl && !hasText) {
                    throw new BadRequestException("media.url_or_text_required", Map.of("mediaType", type.name()));
                }
            }

            project.addMedia(ProjectMedia.builder()
                    .mediaType(type)
                    .url(m.getUrl())
                    .externalUrl(m.getExternalUrl())
                    .embedUrl(m.getEmbedUrl())
                    .caption(m.getCaption())
                    .sortOrder(m.getSortOrder() != null ? m.getSortOrder() : 0)
                    .build());
        }
    }

    // ============================================================
    // CORE BUILD / APPLY / VALIDATE
    // ============================================================
    private Project buildProject(ProjectCreateRequest dto) {
        Project project = new Project();

        applyBaseFields(project, dto);

        if (dto.getContentLanguages() != null && dto.getContentLanguages().contains(Language.CKB)) {
            if (dto.getCkbContent() != null) {
                project.setCkbContent(ProjectContentBlock.builder()
                        .title(dto.getCkbContent().getTitle())
                        .description(dto.getCkbContent().getDescription())
                        .location(dto.getCkbContent().getLocation())
                        .build());
            }
        } else {
            project.setCkbContent(null);
        }

        if (dto.getContentLanguages() != null && dto.getContentLanguages().contains(Language.KMR)) {
            if (dto.getKmrContent() != null) {
                project.setKmrContent(ProjectContentBlock.builder()
                        .title(dto.getKmrContent().getTitle())
                        .description(dto.getKmrContent().getDescription())
                        .location(dto.getKmrContent().getLocation())
                        .build());
            }
        } else {
            project.setKmrContent(null);
        }

        return project;
    }

    private void applyBaseFields(Project project, ProjectCreateRequest dto) {
        project.setCoverUrl(dto.getCoverUrl());
        project.setProjectType(dto.getProjectType());
        project.setProjectDate(dto.getProjectDate());

        project.getContentLanguages().clear();
        if (dto.getContentLanguages() != null) {
            project.getContentLanguages().addAll(dto.getContentLanguages());
        }
    }

    private void validate(ProjectCreateRequest dto) {
        if (dto == null) throw new BadRequestException("request.required", Map.of());

        if (isBlank(dto.getProjectType())) {
            throw new BadRequestException("project.type_required", Map.of());
        }

        if (dto.getContentLanguages() == null || dto.getContentLanguages().isEmpty()) {
            throw new BadRequestException("project.languages_required", Map.of());
        }

        if (dto.getContentLanguages().contains(Language.CKB)) {
            if (dto.getCkbContent() == null || isBlank(dto.getCkbContent().getTitle())) {
                throw new BadRequestException("project.ckb_title_required", Map.of());
            }
        }

        if (dto.getContentLanguages().contains(Language.KMR)) {
            if (dto.getKmrContent() == null || isBlank(dto.getKmrContent().getTitle())) {
                throw new BadRequestException("project.kmr_title_required", Map.of());
            }
        }
    }

    // ============================================================
    // ATTACH RELATIONS
    // ============================================================
    private void attachAllContents(Project project, ProjectCreateRequest dto) {
        attachContents(project, dto.getContentsCkb(), Language.CKB);
        attachContents(project, dto.getContentsKmr(), Language.KMR);
    }

    private void attachContents(Project project, List<String> names, Language lang) {
        if (names == null || names.isEmpty()) return;

        Map<String, ProjectContent> map = ensureContents(names);

        if (lang == Language.CKB) {
            for (String name : names) {
                ProjectContent c = map.get(name.toLowerCase());
                if (c != null) project.getContentsCkb().add(c);
            }
        } else {
            for (String name : names) {
                ProjectContent c = map.get(name.toLowerCase());
                if (c != null) project.getContentsKmr().add(c);
            }
        }
    }

    private Map<String, ProjectContent> ensureContents(List<String> names) {
        Map<String, ProjectContent> result = new HashMap<>();
        for (String raw : names) {
            String name = safe(raw).trim();
            if (name.isEmpty()) continue;

            String key = name.toLowerCase();
            ProjectContent existing = projectContentRepository.findByNameIgnoreCase(name).orElse(null);
            if (existing == null) {
                existing = projectContentRepository.save(ProjectContent.builder().name(name).build());
            }
            result.put(key, existing);
        }
        return result;
    }

    private void attachAllTags(Project project, ProjectCreateRequest dto) {
        attachTags(project, dto.getTagsCkb(), Language.CKB);
        attachTags(project, dto.getTagsKmr(), Language.KMR);
    }

    private void attachTags(Project project, List<String> names, Language lang) {
        if (names == null || names.isEmpty()) return;

        Map<String, ProjectTag> map = ensureTags(names);

        if (lang == Language.CKB) {
            for (String name : names) {
                ProjectTag t = map.get(name.toLowerCase());
                if (t != null) project.getTagsCkb().add(t);
            }
        } else {
            for (String name : names) {
                ProjectTag t = map.get(name.toLowerCase());
                if (t != null) project.getTagsKmr().add(t);
            }
        }
    }

    private Map<String, ProjectTag> ensureTags(List<String> names) {
        Map<String, ProjectTag> result = new HashMap<>();
        for (String raw : names) {
            String name = safe(raw).trim();
            if (name.isEmpty()) continue;

            String key = name.toLowerCase();
            ProjectTag existing = projectTagRepository.findByNameIgnoreCase(name).orElse(null);
            if (existing == null) {
                existing = projectTagRepository.save(ProjectTag.builder().name(name).build());
            }
            result.put(key, existing);
        }
        return result;
    }

    private void attachAllKeywords(Project project, ProjectCreateRequest dto) {
        attachKeywords(project, dto.getKeywordsCkb(), Language.CKB);
        attachKeywords(project, dto.getKeywordsKmr(), Language.KMR);
    }

    private void attachKeywords(Project project, List<String> names, Language lang) {
        if (names == null || names.isEmpty()) return;

        Map<String, ProjectKeyword> map = ensureKeywords(names);

        if (lang == Language.CKB) {
            for (String name : names) {
                ProjectKeyword k = map.get(name.toLowerCase());
                if (k != null) project.getKeywordsCkb().add(k);
            }
        } else {
            for (String name : names) {
                ProjectKeyword k = map.get(name.toLowerCase());
                if (k != null) project.getKeywordsKmr().add(k);
            }
        }
    }

    private Map<String, ProjectKeyword> ensureKeywords(List<String> names) {
        Map<String, ProjectKeyword> result = new HashMap<>();
        for (String raw : names) {
            String name = safe(raw).trim();
            if (name.isEmpty()) continue;

            String key = name.toLowerCase();
            ProjectKeyword existing = projectKeywordRepository.findByNameIgnoreCase(name).orElse(null);
            if (existing == null) {
                existing = projectKeywordRepository.save(ProjectKeyword.builder().name(name).build());
            }
            result.put(key, existing);
        }
        return result;
    }

    // ============================================================
    // AUDIT LOG (FIXED for your ProjectLog fields ✅)
    // ============================================================
    private void createAuditLog(Project project, String action, String message) {
        try {
            ProjectLog logEntry = ProjectLog.builder()
                    .project(project)
                    .action(action)
                    .fieldName("SUMMARY")
                    .oldValue(null)
                    .newValue(message)
                    .createdAt(LocalDateTime.now())
                    .build();
            projectLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to create project log | projectId={}", project != null ? project.getId() : null, e);
        }
    }

    // ============================================================
    // UTIL
    // ============================================================
    private ProjectMediaType detectMediaType(MultipartFile file) {
        String ct = safe(file.getContentType()).toLowerCase();
        String name = safe(file.getOriginalFilename()).toLowerCase();

        if (ct.startsWith("image/") || name.matches(".*\\.(png|jpg|jpeg|gif|webp)$")) return ProjectMediaType.IMAGE;
        if (ct.startsWith("video/") || name.matches(".*\\.(mp4|webm|mov|mkv)$")) return ProjectMediaType.VIDEO;
        if (ct.startsWith("audio/") || name.matches(".*\\.(mp3|wav|ogg|m4a)$")) return ProjectMediaType.AUDIO;
        if (name.endsWith(".pdf")) return ProjectMediaType.PDF;

        return ProjectMediaType.DOCUMENT;
    }

    private String traceId() {
        String t = MDC.get("traceId");
        return t != null ? t : UUID.randomUUID().toString();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String safe(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String safe(Long v) {
        return v == null ? "null" : String.valueOf(v);
    }

    private String safeTitle(Project p) {
        if (p == null) return "";
        if (p.getCkbContent() != null && !isBlank(p.getCkbContent().getTitle())) return p.getCkbContent().getTitle();
        if (p.getKmrContent() != null && !isBlank(p.getKmrContent().getTitle())) return p.getKmrContent().getTitle();
        return "project#" + p.getId();
    }

    private record UploadedMedia(ProjectMediaType mediaType, String url, String caption, int sortOrder) {}
}
