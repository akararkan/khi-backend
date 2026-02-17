package ak.dev.khi_backend.khi_app.service.project;

import ak.dev.khi_backend.khi_app.dto.project.ProjectCreateRequest;
import ak.dev.khi_backend.khi_app.dto.project.ProjectMediaCreateRequest;
import ak.dev.khi_backend.khi_app.dto.project.ProjectMediaResponse;
import ak.dev.khi_backend.khi_app.dto.project.ProjectResponse;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.project.ProjectMediaType;
import ak.dev.khi_backend.khi_app.enums.project.ProjectStatus;
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
import org.springframework.transaction.annotation.Transactional;
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

    private final ProjectRepository          projectRepository;
    private final ProjectContentRepository   projectContentRepository;
    private final ProjectTagRepository       projectTagRepository;
    private final ProjectKeywordRepository   projectKeywordRepository;
    private final ProjectLogRepository       projectLogRepository;
    private final ProjectMediaRepository     projectMediaRepository;
    private final S3Service                  s3Service;
    private final PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager em;

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    // ===========================
    // CREATE  (JSON only)
    // ===========================
    public Project create(ProjectCreateRequest dto) {
        String traceId = traceId();
        log.info("Creating project | langs={} | traceId={}", dto != null ? dto.getContentLanguages() : null, traceId);

        validate(dto, true);

        try {
            Project saved = tx().execute(status -> {
                Project project = buildProject(dto);
                attachAllContents(project, dto);
                attachAllTags(project, dto);
                attachAllKeywords(project, dto);
                attachMediaFromDto(project, dto.getMedia());

                Project p = projectRepository.save(project);
                auditLog(p, "CREATE", "Created project: " + safeTitle(p));
                return p;
            });

            log.info("Project created | id={} | traceId={}", saved.getId(), traceId);
            return saved;

        } catch (AppException ex) { throw ex; }
        catch (DataIntegrityViolationException ex) {
            throw new ConflictException("project.conflict", Map.of("traceId", safe(traceId)));
        } catch (Exception ex) {
            log.error("Unexpected error creating project | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "create", "traceId", safe(traceId)));
        }
    }

    // ===========================
    // CREATE  (with files)
    // ===========================
    public Project create(ProjectCreateRequest dto,
                          MultipartFile cover,
                          List<MultipartFile> mediaFiles) throws IOException {
        String traceId  = traceId();
        int    mcnt     = mediaFiles != null ? mediaFiles.size() : 0;
        log.info("Creating project with files | mediaFiles={} | langs={} | traceId={}",
                mcnt, dto != null ? dto.getContentLanguages() : null, traceId);

        validate(dto, false);

        String dtoCoverUrl   = dto.getCoverUrl();
        List<UploadedMedia> uploaded = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, Math.max(2, 1 + mcnt)));

        try {
            CompletableFuture<String> coverFuture = CompletableFuture.completedFuture(dtoCoverUrl);
            if (cover != null && !cover.isEmpty()) {
                coverFuture = CompletableFuture.supplyAsync(() -> uploadCover(cover), pool);
            }

            List<CompletableFuture<UploadedMedia>> mediaFutures = buildMediaFutures(mediaFiles, pool);
            joinAll(mediaFutures, uploaded);

            String coverUrl = safe(coverFuture.join()).trim();
            if (isBlank(coverUrl)) {
                throw new BadRequestException("project.cover_required", Map.of("traceId", safe(traceId)));
            }

            Project saved = tx().execute(status -> {
                Project p = buildProject(dto);
                p.setCoverUrl(coverUrl);
                attachAllContents(p, dto);
                attachAllTags(p, dto);
                attachAllKeywords(p, dto);
                appendUploadedMedia(p, uploaded);
                attachMediaFromDto(p, dto.getMedia());

                Project persisted = projectRepository.save(p);
                auditLog(persisted, "CREATE", "Created project with uploads: " + safeTitle(persisted));
                return persisted;
            });

            log.info("Project created with files | id={} | traceId={}", saved.getId(), traceId);
            return saved;

        } catch (AppException ex) { throw ex; }
        catch (DataIntegrityViolationException ex) {
            throw new ConflictException("project.conflict", Map.of("traceId", safe(traceId)));
        } catch (Exception ex) {
            log.error("Unexpected error creating project with files | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "createWithFiles", "traceId", safe(traceId)));
        } finally { pool.shutdownNow(); }
    }

    // ===========================
    // UPDATE  (JSON only)
    // ===========================
    public Project update(Long projectId, ProjectCreateRequest dto) {
        String traceId = traceId();
        log.info("Updating project | id={} | traceId={}", projectId, traceId);

        validate(dto, true);

        try {
            Project saved = tx().execute(status -> {
                Project project = findOrThrow(projectId);
                applyUpdate(project, dto, project.getCoverUrl());
                Project persisted = projectRepository.save(project);
                auditLog(persisted, "UPDATE", "Updated project: " + safeTitle(persisted));
                return persisted;
            });

            log.info("Project updated | id={} | traceId={}", saved.getId(), traceId);
            return saved;

        } catch (AppException ex) { throw ex; }
        catch (DataIntegrityViolationException ex) {
            throw new ConflictException("project.conflict", Map.of("traceId", safe(traceId)));
        } catch (Exception ex) {
            log.error("Unexpected error updating project | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "update", "traceId", safe(traceId)));
        }
    }

    // ===========================
    // UPDATE  (with files)
    // ===========================
    public Project updateWithFiles(Long projectId,
                                   ProjectCreateRequest dto,
                                   MultipartFile cover,
                                   List<MultipartFile> mediaFiles) throws IOException {
        String traceId = traceId();
        int    mcnt    = mediaFiles != null ? mediaFiles.size() : 0;
        log.info("Updating project with files | id={} | mediaFiles={} | traceId={}", projectId, mcnt, traceId);

        validate(dto, false);

        List<UploadedMedia> uploaded = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, Math.max(2, 1 + mcnt)));

        try {
            CompletableFuture<String> coverFuture = CompletableFuture.completedFuture(dto.getCoverUrl());
            if (cover != null && !cover.isEmpty()) {
                coverFuture = CompletableFuture.supplyAsync(() -> uploadCover(cover), pool);
            }

            List<CompletableFuture<UploadedMedia>> mediaFutures = buildMediaFutures(mediaFiles, pool);
            joinAll(mediaFutures, uploaded);

            String coverUrl = safe(coverFuture.join()).trim();
            if (isBlank(coverUrl)) {
                throw new BadRequestException("project.cover_required", Map.of("traceId", safe(traceId)));
            }

            Project saved = tx().execute(status -> {
                Project project = findOrThrow(projectId);
                applyUpdate(project, dto, coverUrl);
                appendUploadedMedia(project, uploaded);
                Project persisted = projectRepository.save(project);
                auditLog(persisted, "UPDATE", "Updated project with uploads: " + safeTitle(persisted));
                return persisted;
            });

            log.info("Project updated with files | id={} | traceId={}", saved.getId(), traceId);
            return saved;

        } catch (AppException ex) { throw ex; }
        catch (DataIntegrityViolationException ex) {
            throw new ConflictException("project.conflict", Map.of("traceId", safe(traceId)));
        } catch (Exception ex) {
            log.error("Unexpected error updating project with files | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "updateWithFiles", "traceId", safe(traceId)));
        } finally { pool.shutdownNow(); }
    }

    // ===========================
    // DELETE
    // ===========================
    @Transactional
    public void delete(Long projectId) {
        String traceId = traceId();
        log.info("Deleting project | id={} | traceId={}", projectId, traceId);

        try {
            tx().execute(status -> {
                Project project = findOrThrow(projectId);
                String  title   = safeTitle(project);

                int logCount = projectLogRepository.deleteByProject(project);
                log.debug("Purged {} project_log rows for project id={}", logCount, projectId);

                projectRepository.delete(project);
                log.info("Project deleted | id={} title='{}' | traceId={}", projectId, title, traceId);

                return null;
            });

        } catch (AppException ex) { throw ex; }
        catch (Exception ex) {
            log.error("Unexpected error deleting project | id={} | traceId={}", projectId, traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "delete", "traceId", safe(traceId)));
        }
    }

    // ============================================================
    // QUERY HELPERS
    // ============================================================
    public Page<ProjectResponse> getAllResponse(int page, int size) {
        return projectRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(this::toResponse);
    }

    public Page<ProjectResponse> searchByTagResponse(String tag, int page, int size) {
        if (isBlank(tag)) throw new BadRequestException("tag.required", Map.of());
        return projectRepository.searchByTag(tag, PageRequest.of(page, size)).map(this::toResponse);
    }

    public Page<ProjectResponse> searchByKeywordResponse(String keyword, int page, int size) {
        if (isBlank(keyword)) throw new BadRequestException("keyword.required", Map.of());
        return projectRepository.searchByKeyword(keyword, PageRequest.of(page, size)).map(this::toResponse);
    }

    // ============================================================
    // INTERNAL — UPDATE HELPER
    // ============================================================
    private void applyUpdate(Project project, ProjectCreateRequest dto, String resolvedCoverUrl) {
        // Base scalar fields
        project.setCoverUrl(resolvedCoverUrl);
        project.setProjectDate(dto.getProjectDate());

        // ✅ Bilingual project type
        project.setProjectTypeCkb(dto.getProjectTypeCkb());
        project.setProjectTypeKmr(dto.getProjectTypeKmr());

        // ✅ Project status (default ONGOING if not provided)
        project.setStatus(dto.getStatus() != null ? dto.getStatus() : ProjectStatus.ONGOING);

        // Content languages
        project.getContentLanguages().clear();
        if (dto.getContentLanguages() != null) {
            project.getContentLanguages().addAll(dto.getContentLanguages());
        }

        // Embedded content blocks
        if (dto.getContentLanguages() != null && dto.getContentLanguages().contains(Language.CKB)) {
            project.setCkbContent(dto.getCkbContent() != null
                    ? ProjectContentBlock.builder()
                    .title(dto.getCkbContent().getTitle())
                    .description(dto.getCkbContent().getDescription())
                    .location(dto.getCkbContent().getLocation())
                    .build()
                    : null);
        } else {
            project.setCkbContent(null);
        }

        if (dto.getContentLanguages() != null && dto.getContentLanguages().contains(Language.KMR)) {
            project.setKmrContent(dto.getKmrContent() != null
                    ? ProjectContentBlock.builder()
                    .title(dto.getKmrContent().getTitle())
                    .description(dto.getKmrContent().getDescription())
                    .location(dto.getKmrContent().getLocation())
                    .build()
                    : null);
        } else {
            project.setKmrContent(null);
        }

        // Clear then re-attach all ManyToMany relations
        project.getContentsCkb().clear();
        project.getContentsKmr().clear();
        project.getTagsCkb().clear();
        project.getTagsKmr().clear();
        project.getKeywordsCkb().clear();
        project.getKeywordsKmr().clear();

        attachAllContents(project, dto);
        attachAllTags(project, dto);
        attachAllKeywords(project, dto);

        // Media: clear existing then re-attach from DTO
        if (dto.getMedia() != null) {
            project.getMedia().clear();
        }
        attachMediaFromDto(project, dto.getMedia());
    }

    // ============================================================
    // INTERNAL — S3 UPLOAD HELPERS
    // ============================================================
    private String uploadCover(MultipartFile file) {
        try {
            return s3Service.upload(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    ProjectMediaType.IMAGE
            );
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private List<CompletableFuture<UploadedMedia>> buildMediaFutures(
            List<MultipartFile> files, ExecutorService pool) {

        List<CompletableFuture<UploadedMedia>> futures = new ArrayList<>();
        if (files == null || files.isEmpty()) return futures;

        int order = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            final int sortOrder = order++;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    ProjectMediaType type = detectMediaType(file);
                    String url = s3Service.upload(
                            file.getBytes(),
                            file.getOriginalFilename(),
                            file.getContentType(),
                            type
                    );
                    return new UploadedMedia(type, url, null, sortOrder);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, pool));
        }
        return futures;
    }

    private void joinAll(List<CompletableFuture<UploadedMedia>> futures,
                         List<UploadedMedia> target) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<UploadedMedia> f : futures) target.add(f.join());
        } catch (CompletionException ex) {
            Throwable root = ex.getCause() != null ? ex.getCause() : ex;
            if (root instanceof RuntimeException re) throw re;
            throw ex;
        }
    }

    private void appendUploadedMedia(Project project, List<UploadedMedia> uploaded) {
        for (UploadedMedia um : uploaded) {
            project.addMedia(ProjectMedia.builder()
                    .mediaType(um.mediaType())
                    .url(um.url())
                    .caption(um.caption())
                    .sortOrder(um.sortOrder())
                    .build());
        }
    }

    // ============================================================
    // INTERNAL — BUILD / VALIDATE
    // ============================================================
    private Project buildProject(ProjectCreateRequest dto) {
        Project project = new Project();
        applyBaseFields(project, dto);

        if (dto.getContentLanguages() != null && dto.getContentLanguages().contains(Language.CKB)
                && dto.getCkbContent() != null) {
            project.setCkbContent(ProjectContentBlock.builder()
                    .title(dto.getCkbContent().getTitle())
                    .description(dto.getCkbContent().getDescription())
                    .location(dto.getCkbContent().getLocation())
                    .build());
        }

        if (dto.getContentLanguages() != null && dto.getContentLanguages().contains(Language.KMR)
                && dto.getKmrContent() != null) {
            project.setKmrContent(ProjectContentBlock.builder()
                    .title(dto.getKmrContent().getTitle())
                    .description(dto.getKmrContent().getDescription())
                    .location(dto.getKmrContent().getLocation())
                    .build());
        }

        return project;
    }

    private void applyBaseFields(Project project, ProjectCreateRequest dto) {
        project.setCoverUrl(dto.getCoverUrl());

        // ✅ Bilingual project type
        project.setProjectTypeCkb(dto.getProjectTypeCkb());
        project.setProjectTypeKmr(dto.getProjectTypeKmr());

        // ✅ Status (default ONGOING)
        project.setStatus(dto.getStatus() != null ? dto.getStatus() : ProjectStatus.ONGOING);

        project.setProjectDate(dto.getProjectDate());
        project.getContentLanguages().clear();
        if (dto.getContentLanguages() != null) {
            project.getContentLanguages().addAll(dto.getContentLanguages());
        }
    }

    private void validate(ProjectCreateRequest dto, boolean requireCoverUrl) {
        if (dto == null) throw new BadRequestException("request.required", Map.of());

        // ✅ At least one language must have a project type
        boolean hasCkbLang = dto.getContentLanguages() != null && dto.getContentLanguages().contains(Language.CKB);
        boolean hasKmrLang = dto.getContentLanguages() != null && dto.getContentLanguages().contains(Language.KMR);

        if (hasCkbLang && isBlank(dto.getProjectTypeCkb()))
            throw new BadRequestException("project.ckb_type_required", Map.of());
        if (hasKmrLang && isBlank(dto.getProjectTypeKmr()))
            throw new BadRequestException("project.kmr_type_required", Map.of());

        if (dto.getContentLanguages() == null || dto.getContentLanguages().isEmpty())
            throw new BadRequestException("project.languages_required", Map.of());
        if (requireCoverUrl && isBlank(dto.getCoverUrl()))
            throw new BadRequestException("project.cover_required", Map.of());
        if (hasCkbLang && (dto.getCkbContent() == null || isBlank(dto.getCkbContent().getTitle())))
            throw new BadRequestException("project.ckb_title_required", Map.of());
        if (hasKmrLang && (dto.getKmrContent() == null || isBlank(dto.getKmrContent().getTitle())))
            throw new BadRequestException("project.kmr_title_required", Map.of());
    }

    // ============================================================
    // INTERNAL — ATTACH RELATIONS
    // ============================================================
    private void attachAllContents(Project p, ProjectCreateRequest dto) {
        attachContents(p, dto.getContentsCkb(), Language.CKB);
        attachContents(p, dto.getContentsKmr(), Language.KMR);
    }

    private void attachContents(Project project, List<String> names, Language lang) {
        if (names == null || names.isEmpty()) return;
        Map<String, ProjectContent> map = ensureContents(names);
        Set<Long> seen = new HashSet<>();
        for (String raw : names) {
            ProjectContent c = map.get(normKey(raw));
            if (c != null && seen.add(c.getId())) {
                if (lang == Language.CKB) project.getContentsCkb().add(c);
                else                      project.getContentsKmr().add(c);
            }
        }
    }

    private Map<String, ProjectContent> ensureContents(List<String> names) {
        Map<String, ProjectContent> result = new HashMap<>();
        for (String raw : names) {
            String name = safe(raw).trim();
            if (name.isEmpty()) continue;
            result.put(name.toLowerCase(), projectContentRepository
                    .findByNameIgnoreCase(name)
                    .orElseGet(() -> projectContentRepository.save(
                            ProjectContent.builder().name(name).build())));
        }
        return result;
    }

    private void attachAllTags(Project p, ProjectCreateRequest dto) {
        attachTags(p, dto.getTagsCkb(), Language.CKB);
        attachTags(p, dto.getTagsKmr(), Language.KMR);
    }

    private void attachTags(Project project, List<String> names, Language lang) {
        if (names == null || names.isEmpty()) return;
        Map<String, ProjectTag> map = ensureTags(names);
        Set<Long> seen = new HashSet<>();
        for (String raw : names) {
            ProjectTag t = map.get(normKey(raw));
            if (t != null && seen.add(t.getId())) {
                if (lang == Language.CKB) project.getTagsCkb().add(t);
                else                      project.getTagsKmr().add(t);
            }
        }
    }

    private Map<String, ProjectTag> ensureTags(List<String> names) {
        Map<String, ProjectTag> result = new HashMap<>();
        for (String raw : names) {
            String name = safe(raw).trim();
            if (name.isEmpty()) continue;
            result.put(name.toLowerCase(), projectTagRepository
                    .findByNameIgnoreCase(name)
                    .orElseGet(() -> projectTagRepository.save(
                            ProjectTag.builder().name(name).build())));
        }
        return result;
    }

    private void attachAllKeywords(Project p, ProjectCreateRequest dto) {
        attachKeywords(p, dto.getKeywordsCkb(), Language.CKB);
        attachKeywords(p, dto.getKeywordsKmr(), Language.KMR);
    }

    private void attachKeywords(Project project, List<String> names, Language lang) {
        if (names == null || names.isEmpty()) return;
        Map<String, ProjectKeyword> map = ensureKeywords(names);
        Set<Long> seen = new HashSet<>();
        for (String raw : names) {
            ProjectKeyword k = map.get(normKey(raw));
            if (k != null && seen.add(k.getId())) {
                if (lang == Language.CKB) project.getKeywordsCkb().add(k);
                else                      project.getKeywordsKmr().add(k);
            }
        }
    }

    private Map<String, ProjectKeyword> ensureKeywords(List<String> names) {
        Map<String, ProjectKeyword> result = new HashMap<>();
        for (String raw : names) {
            String name = safe(raw).trim();
            if (name.isEmpty()) continue;
            result.put(name.toLowerCase(), projectKeywordRepository
                    .findByNameIgnoreCase(name)
                    .orElseGet(() -> projectKeywordRepository.save(
                            ProjectKeyword.builder().name(name).build())));
        }
        return result;
    }

    // ============================================================
    // INTERNAL — MEDIA DTO ATTACH
    // ============================================================
    private void attachMediaFromDto(Project project, List<ProjectMediaCreateRequest> mediaList) {
        if (mediaList == null || mediaList.isEmpty()) return;

        for (ProjectMediaCreateRequest m : mediaList) {
            if (m == null) continue;

            ProjectMediaType type;
            try {
                type = ProjectMediaType.valueOf(m.getMediaType());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("media.type_invalid",
                        Map.of("mediaType", safe(m.getMediaType())));
            }

            boolean hasUrl      = !isBlank(m.getUrl());
            boolean hasExternal = !isBlank(m.getExternalUrl());
            boolean hasEmbed    = !isBlank(m.getEmbedUrl());
            boolean hasText     = !isBlank(m.getTextBody());

            if (type == ProjectMediaType.AUDIO || type == ProjectMediaType.VIDEO) {
                if (!hasUrl && !hasExternal && !hasEmbed)
                    throw new BadRequestException("media.audio_video_requires_url_or_link",
                            Map.of("mediaType", type.name()));
            } else {
                if (!hasUrl && !hasText)
                    throw new BadRequestException("media.url_or_text_required",
                            Map.of("mediaType", type.name()));
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
    // INTERNAL — RESPONSE MAPPER
    // ============================================================
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

        List<String> contentsCkb  = toNames(project.getContentsCkb(), ProjectContent::getName);
        List<String> contentsKmr  = toNames(project.getContentsKmr(), ProjectContent::getName);
        List<String> tagsCkb      = toNames(project.getTagsCkb(),     ProjectTag::getName);
        List<String> tagsKmr      = toNames(project.getTagsKmr(),     ProjectTag::getName);
        List<String> keywordsCkb  = toNames(project.getKeywordsCkb(), ProjectKeyword::getName);
        List<String> keywordsKmr  = toNames(project.getKeywordsKmr(), ProjectKeyword::getName);

        List<ProjectMediaResponse> media = new ArrayList<>();
        if (project.getMedia() != null && !project.getMedia().isEmpty()) {
            List<ProjectMedia> sorted = new ArrayList<>(project.getMedia());
            sorted.sort(Comparator.comparingInt(ProjectMedia::getSortOrder)
                    .thenComparing(ProjectMedia::getId));
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

        Instant createdAt = project.getCreatedAt() != null
                ? project.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()
                : null;

        return ProjectResponse.builder()
                .id(project.getId())
                .coverUrl(project.getCoverUrl())
                // ✅ Bilingual project type
                .projectTypeCkb(project.getProjectTypeCkb())
                .projectTypeKmr(project.getProjectTypeKmr())
                // ✅ Status
                .status(project.getStatus())
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

    @FunctionalInterface
    private interface NameExtractor<T> { String name(T t); }

    private <T> List<String> toNames(Iterable<T> items, NameExtractor<T> fn) {
        List<String> out = new ArrayList<>();
        if (items == null) return out;
        for (T item : items) {
            if (item != null && !isBlank(fn.name(item))) out.add(fn.name(item));
        }
        return out;
    }

    // ============================================================
    // AUDIT LOG
    // ============================================================
    private void auditLog(Project project, String action, String message) {
        try {
            projectLogRepository.save(ProjectLog.builder()
                    .project(project)
                    .action(action)
                    .fieldName("SUMMARY")
                    .oldValue(null)
                    .newValue(message)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to write project audit log | projectId={} | action={}",
                    project != null ? project.getId() : null, action, e);
        }
    }

    // ============================================================
    // UTIL
    // ============================================================
    private Project findOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException(
                        "project.not_found", Map.of("id", safe(projectId))));
    }

    private ProjectMediaType detectMediaType(MultipartFile file) {
        String ct   = safe(file.getContentType()).toLowerCase();
        String name = safe(file.getOriginalFilename()).toLowerCase();
        if (ct.startsWith("image/") || name.matches(".*\\.(png|jpg|jpeg|gif|webp)$")) return ProjectMediaType.IMAGE;
        if (ct.startsWith("video/") || name.matches(".*\\.(mp4|webm|mov|mkv)$"))      return ProjectMediaType.VIDEO;
        if (ct.startsWith("audio/") || name.matches(".*\\.(mp3|wav|ogg|m4a)$"))       return ProjectMediaType.AUDIO;
        if (name.endsWith(".pdf"))                                                      return ProjectMediaType.PDF;
        return ProjectMediaType.DOCUMENT;
    }

    private String traceId() {
        String t = MDC.get("traceId");
        return t != null ? t : UUID.randomUUID().toString();
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private String safe(Object o) { return o == null ? "" : String.valueOf(o); }

    private String safe(Long v)   { return v == null ? "null" : String.valueOf(v); }

    private String safeTitle(Project p) {
        if (p == null) return "";
        if (p.getCkbContent() != null && !isBlank(p.getCkbContent().getTitle())) return p.getCkbContent().getTitle();
        if (p.getKmrContent() != null && !isBlank(p.getKmrContent().getTitle())) return p.getKmrContent().getTitle();
        return "project#" + p.getId();
    }

    private String normKey(String raw) { return safe(raw).trim().toLowerCase(); }

    private record UploadedMedia(ProjectMediaType mediaType, String url, String caption, int sortOrder) {}
}