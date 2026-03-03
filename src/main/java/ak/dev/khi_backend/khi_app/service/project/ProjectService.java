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

/**
 * سێرڤیسی پرۆژە - بەڕێوەبردنی هەموو کردارەکانی پرۆژەکان
 *
 * لیستی هەڵە کوردیەکان کە بەکاردێن:
 *
 * ١. project.conflict - "کێشە لە دروستکردنی پرۆژە: دەستکاری پێشوەختە یان کێشەی تەکنیکی هەیە" ( Conflict )
 * ٢. error.db - "هەڵەی ناوخۆیی داتابەیس: کێشە لە پەیوەندی بە بنکەدراوە" ( Internal Server Error )
 * ٣. request.required - "داواکاری پێویستە: زانیاری نێردراو بەتاڵە" ( Bad Request )
 * ٤. project.cover_required - "وێنەی بەرگی پێویستە: دەبێت وێنەی سەرەکی بۆ پرۆژەکە دابنرێت" ( Bad Request )
 * ٥. tag.required - "تاگی پێویستە: بۆ گەڕان دەبێت تاگ بنووسرێت" ( Bad Request )
 * ٦. keyword.required - "کلیلەووشەی پێویستە: بۆ گەڕان دەبێت کلیلەووشە بنووسرێت" ( Bad Request )
 * ٧. project.ckb_type_required - "جۆری پرۆژە بە کوردیی ناوەندی (سۆرانی) پێویستە" ( Bad Request )
 * ٨. project.kmr_type_required - "جۆری پرۆژە بە کوردیی باکووری (کورمانجی) پێویستە" ( Bad Request )
 * ٩. project.languages_required - "زمانێک پێویستە: دەبێت لانیکەم یەک زمان هەڵبژێردرێت" ( Bad Request )
 * ١٠. project.ckb_title_required - "ناونیشانی کوردیی ناوەندی پێویستە" ( Bad Request )
 * ١١. project.kmr_title_required - "ناونیشانی کوردیی باکووری پێویستە" ( Bad Request )
 * ١٢. media.type_invalid - "جۆری میدیا هەڵەیە: جۆرەکە ناناسرێتەوە (IMAGE, VIDEO, AUDIO, PDF, DOCUMENT)" ( Bad Request )
 * ١٣. media.audio_video_requires_url_or_link - "ئۆدیۆ یان ڤیدیۆ پێویستی بە لینکی ڕاستەقینە یان لینکی دەرەکیە" ( Bad Request )
 * ١٤. media.url_or_text_required - "لینکی وێنە یان دەقی پێناسە پێویستە" ( Bad Request )
 * ١٥. project.not_found - "پرۆژەکە نەدۆزرایەوە: ئایدییەکە بوونی نییە لە سیستەم" ( Not Found )
 */
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
    // دروستکردن (تەنها JSON)
    // ===========================
    /**
     * دروستکردنی پرۆژەی نوێ بە بەکارهێنانی زانیاری JSON
     *
     * @throws BadRequestException    - ئەگەر زانیاریەکان نەبن یان ناتەواون ("داواکاری پێویستە")
     * @throws ConflictException      - ئەگەر کێشەیەک لە بنکەدراوە هەبێت ("کێشە لە دروستکردنی پرۆژە")
     * @throws AppException          - هەڵە ناوخۆییەکانی دیکە
     */
    public Project create(ProjectCreateRequest dto) {
        String traceId = traceId();
        log.info("دروستکردنی پرۆژە | زمانەکان={} | traceId={}", dto != null ? dto.getContentLanguages() : null, traceId);

        validate(dto, true);

        try {
            Project saved = tx().execute(status -> {
                Project project = buildProject(dto);
                attachAllContents(project, dto);
                attachAllTags(project, dto);
                attachAllKeywords(project, dto);
                attachMediaFromDto(project, dto.getMedia());

                Project p = projectRepository.save(project);
                auditLog(p, "CREATE", "پرۆژە دروستکرا: " + safeTitle(p));
                return p;
            });

            log.info("پرۆژە دروستکرا | id={} | traceId={}", saved.getId(), traceId);
            return saved;

        } catch (AppException ex) { throw ex; }
        catch (DataIntegrityViolationException ex) {
            // هەڵە: کێشە لە تێکەڵکردنی داتا - ڕەنگە پرۆژەیەکی هاوشێوە بوونی هەبێت
            throw new ConflictException("project.conflict", Map.of("traceId", safe(traceId)));
        } catch (Exception ex) {
            log.error("هەڵەی چاوەڕواننەکراو لە دروستکردنی پرۆژە | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "create", "traceId", safe(traceId)));
        }
    }

    // ===========================
    // دروستکردن (لەگەڵ فایلەکان)
    // ===========================
    /**
     * دروستکردنی پرۆژە لەگەڵ فایلی وێنە و میدیا
     *
     * @throws BadRequestException    - ئەگەر وێنەی بەرگ نەبێت ("وێنەی بەرگی پێویستە")
     * @throws ConflictException      - کێشەی داتابەیس ("کێشە لە دروستکردنی پرۆژە")
     * @throws IOException           - کێشە لە خوێندنەوەی فایلەکان
     * @throws CompletionException   - کێشە لە ناردنی فایلەکان بۆ S3
     */
    public Project create(ProjectCreateRequest dto,
                          MultipartFile cover,
                          List<MultipartFile> mediaFiles) throws IOException {
        String traceId  = traceId();
        int    mcnt     = mediaFiles != null ? mediaFiles.size() : 0;
        log.info("دروستکردنی پرۆژە لەگەڵ فایلەکان | ژماری میدیا={} | زمانەکان={} | traceId={}",
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
                // هەڵە: وێنەی بەرگی پرۆژە نەنێردراوە
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
                auditLog(persisted, "CREATE", "پرۆژە لەگەڵ فایل دروستکرا: " + safeTitle(persisted));
                return persisted;
            });

            log.info("پرۆژە لەگەڵ فایل دروستکرا | id={} | traceId={}", saved.getId(), traceId);
            return saved;

        } catch (AppException ex) { throw ex; }
        catch (DataIntegrityViolationException ex) {
            throw new ConflictException("project.conflict", Map.of("traceId", safe(traceId)));
        } catch (Exception ex) {
            log.error("هەڵەی چاوەڕواننەکراو | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "createWithFiles", "traceId", safe(traceId)));
        } finally { pool.shutdownNow(); }
    }

    // ===========================
    // نوێکردنەوە (تەنها JSON)
    // ===========================
    /**
     * نوێکردنەوەی زانیاری پرۆژە
     *
     * @throws NotFoundException   - ئەگەر پرۆژەکە نەدۆزرێتەوە ("پرۆژەکە نەدۆزرایەوە")
     * @throws ConflictException   - کێشەی داتابەیس ("کێشە لە نوێکردنەوەی پرۆژە")
     */
    public Project update(Long projectId, ProjectCreateRequest dto) {
        String traceId = traceId();
        log.info("نوێکردنەوەی پرۆژە | id={} | traceId={}", projectId, traceId);

        validate(dto, true);

        try {
            Project saved = tx().execute(status -> {
                Project project = findOrThrow(projectId);
                applyUpdate(project, dto, project.getCoverUrl());
                Project persisted = projectRepository.save(project);
                auditLog(persisted, "UPDATE", "پرۆژە نوێکرایەوە: " + safeTitle(persisted));
                return persisted;
            });

            log.info("پرۆژە نوێکرایەوە | id={} | traceId={}", saved.getId(), traceId);
            return saved;

        } catch (AppException ex) { throw ex; }
        catch (DataIntegrityViolationException ex) {
            throw new ConflictException("project.conflict", Map.of("traceId", safe(traceId)));
        } catch (Exception ex) {
            log.error("هەڵە لە نوێکردنەوەی پرۆژە | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "update", "traceId", safe(traceId)));
        }
    }

    // ===========================
    // نوێکردنەوە (لەگەڵ فایلەکان)
    // ===========================
    /**
     * نوێکردنەوەی پرۆژە لەگەڵ فایلی نوێ
     *
     * @throws NotFoundException    - پرۆژەکە نەدۆزرێتەوە ("پرۆژەکە نەدۆزرایەوە")
     * @throws BadRequestException  - وێنەی بەرگ نەبێت ("وێنەی بەرگی پێویستە")
     * @throws ConflictException    - کێشەی داتابەیس ("کێشە لە نوێکردنەوە")
     */
    public Project updateWithFiles(Long projectId,
                                   ProjectCreateRequest dto,
                                   MultipartFile cover,
                                   List<MultipartFile> mediaFiles) throws IOException {
        String traceId = traceId();
        int    mcnt    = mediaFiles != null ? mediaFiles.size() : 0;
        log.info("نوێکردنەوەی پرۆژە لەگەڵ فایلەکان | id={} | ژماری میدیا={} | traceId={}", projectId, mcnt, traceId);

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
                // هەڵە: وێنەی بەرگ بۆ نوێکردنەوە پێویستە
                throw new BadRequestException("project.cover_required", Map.of("traceId", safe(traceId)));
            }

            Project saved = tx().execute(status -> {
                Project project = findOrThrow(projectId);
                applyUpdate(project, dto, coverUrl);
                appendUploadedMedia(project, uploaded);
                Project persisted = projectRepository.save(project);
                auditLog(persisted, "UPDATE", "پرۆژە لەگەڵ فایل نوێکرایەوە: " + safeTitle(persisted));
                return persisted;
            });

            log.info("پرۆژە لەگەڵ فایل نوێکرایەوە | id={} | traceId={}", saved.getId(), traceId);
            return saved;

        } catch (AppException ex) { throw ex; }
        catch (DataIntegrityViolationException ex) {
            throw new ConflictException("project.conflict", Map.of("traceId", safe(traceId)));
        } catch (Exception ex) {
            log.error("هەڵە لە نوێکردنەوەی پرۆژە لەگەڵ فایلەکان | traceId={}", traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "updateWithFiles", "traceId", safe(traceId)));
        } finally { pool.shutdownNow(); }
    }

    // ===========================
    // سڕینەوە
    // ===========================
    /**
     * سڕینەوەی پرۆژە بە تەواوی
     *
     * @throws NotFoundException  - پرۆژەکە نەدۆزرێتەوە ("پرۆژەکە نەدۆزرایەوە")
     * @throws AppException      - هەڵەی داتابەیس لە کاتی سڕینەوە
     */
    @Transactional
    public void delete(Long projectId) {
        String traceId = traceId();
        log.info("سڕینەوەی پرۆژە | id={} | traceId={}", projectId, traceId);

        try {
            tx().execute(status -> {
                Project project = findOrThrow(projectId);
                String  title   = safeTitle(project);

                int logCount = projectLogRepository.deleteByProject(project);
                log.debug("{} تۆماری چۆنێتیی پرۆژە سڕایەوە بۆ id={}", logCount, projectId);

                projectRepository.delete(project);
                log.info("پرۆژە سڕایەوە | id={} ناونیشان='{}' | traceId={}", projectId, title, traceId);

                return null;
            });

        } catch (AppException ex) { throw ex; }
        catch (Exception ex) {
            log.error("هەڵە لە سڕینەوەی پرۆژە | id={} | traceId={}", projectId, traceId, ex);
            throw Errors.internal("error.db", Map.of("op", "delete", "traceId", safe(traceId)));
        }
    }

    // ============================================================
    // گەڕان و لێکدانەوە (Query Helpers)
    // ============================================================
    public Page<ProjectResponse> getAllResponse(int page, int size) {
        return projectRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(this::toResponse);
    }

    /**
     * گەڕان بەپێی تاگ
     *
     * @throws BadRequestException - ئەگەر تاگ بەتاڵ بێت ("تاگی پێویستە")
     */
    public Page<ProjectResponse> searchByTagResponse(String tag, int page, int size) {
        if (isBlank(tag)) {
            // هەڵە: تاگی گەڕان بەتاڵە
            throw new BadRequestException("tag.required", Map.of());
        }
        return projectRepository.searchByTag(tag, PageRequest.of(page, size)).map(this::toResponse);
    }

    /**
     * گەڕان بەپێی کلیلەووشە
     *
     * @throws BadRequestException - ئەگەر کلیلەووشە بەتاڵ بێت ("کلیلەووشەی پێویستە")
     */
    public Page<ProjectResponse> searchByKeywordResponse(String keyword, int page, int size) {
        if (isBlank(keyword)) {
            // هەڵە: کلیلەووشەی گەڕان بەتاڵە
            throw new BadRequestException("keyword.required", Map.of());
        }
        return projectRepository.searchByKeyword(keyword, PageRequest.of(page, size)).map(this::toResponse);
    }

    // ============================================================
    // یاریدەدەری نوێکردنەوە
    // ============================================================
    private void applyUpdate(Project project, ProjectCreateRequest dto, String resolvedCoverUrl) {
        // زانیاری بنەڕەتی
        project.setCoverUrl(resolvedCoverUrl);
        project.setProjectDate(dto.getProjectDate());

        // جۆری پرۆژە بە دوو زمانی کوردی
        project.setProjectTypeCkb(dto.getProjectTypeCkb());
        project.setProjectTypeKmr(dto.getProjectTypeKmr());

        // دۆخی پرۆژە (بنەڕەتی: بەردەوامە)
        project.setStatus(dto.getStatus() != null ? dto.getStatus() : ProjectStatus.ONGOING);

        // زمانەکان
        project.getContentLanguages().clear();
        if (dto.getContentLanguages() != null) {
            project.getContentLanguages().addAll(dto.getContentLanguages());
        }

        // ناوەڕۆکی بەستەراو (CKB - سۆرانی)
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

        // ناوەڕۆکی بەستەراو (KMR - کورمانجی)
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

        // پاککردنەوەی پەیوەندییە کۆنەکان و دانانی نوێیەکان
        project.getContentsCkb().clear();
        project.getContentsKmr().clear();
        project.getTagsCkb().clear();
        project.getTagsKmr().clear();
        project.getKeywordsCkb().clear();
        project.getKeywordsKmr().clear();

        attachAllContents(project, dto);
        attachAllTags(project, dto);
        attachAllKeywords(project, dto);

        // میدیا: پاککردنەوە و دانانی نوێ
        if (dto.getMedia() != null) {
            project.getMedia().clear();
        }
        attachMediaFromDto(project, dto.getMedia());
    }

    // ============================================================
    // یاریدەدەرەکانی ناردنی S3
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
            // هەڵە: کێشە لە خوێندنەوەی فایلی وێنە
            throw new CompletionException("کێشە لە خوێندنەوەی فایلی وێنە: " + e.getMessage(), e);
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
                    // هەڵە: کێشە لە ناردنی فایل بۆ S3
                    throw new CompletionException("کێشە لە ناردنی فایل: " + file.getOriginalFilename(), e);
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
    // دروستکردن و پشتڕاستکردنەوە
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

        // جۆری پرۆژە بە کوردی
        project.setProjectTypeCkb(dto.getProjectTypeCkb());
        project.setProjectTypeKmr(dto.getProjectTypeKmr());

        // دۆخ (بنەڕەتی بەردەوامە)
        project.setStatus(dto.getStatus() != null ? dto.getStatus() : ProjectStatus.ONGOING);

        project.setProjectDate(dto.getProjectDate());
        project.getContentLanguages().clear();
        if (dto.getContentLanguages() != null) {
            project.getContentLanguages().addAll(dto.getContentLanguages());
        }
    }

    /**
     * پشتڕاستکردنەوەی زانیاری داخڵکراو
     *
     * هەڵەکان بە کوردی:
     * - "request.required" = داواکاری پێویستە (ئەگەر dto بەتاڵ بێت)
     * - "project.ckb_type_required" = جۆری پرۆژە بە کوردیی ناوەندی پێویستە
     * - "project.kmr_type_required" = جۆری پرۆژە بە کوردیی باکووری پێویستە
     * - "project.languages_required" = زمانێک پێویستە (بەتاڵە یان بە بەتاڵییە)
     * - "project.cover_required" = وێنەی بەرگی پێویستە
     * - "project.ckb_title_required" = ناونیشانی کوردیی ناوەندی پێویستە
     * - "project.kmr_title_required" = ناونیشانی کوردیی باکووری پێویستە
     */
    private void validate(ProjectCreateRequest dto, boolean requireCoverUrl) {
        if (dto == null) {
            // هەڵە: داواکاری نەنێردراوە یان بەتاڵە
            throw new BadRequestException("request.required", Map.of());
        }

        // پێویستە لانیکەم یەک جۆر بۆ هەر زمانێک دیاری بکرێت
        boolean hasCkbLang = dto.getContentLanguages() != null && dto.getContentLanguages().contains(Language.CKB);
        boolean hasKmrLang = dto.getContentLanguages() != null && dto.getContentLanguages().contains(Language.KMR);

        if (hasCkbLang && isBlank(dto.getProjectTypeCkb())) {
            // هەڵە: جۆری پرۆژە بۆ زمانی کوردیی ناوەندی (سۆرانی) دیاری نەکراوە
            throw new BadRequestException("project.ckb_type_required", Map.of());
        }
        if (hasKmrLang && isBlank(dto.getProjectTypeKmr())) {
            // هەڵە: جۆری پرۆژە بۆ زمانی کوردیی باکووری (کورمانجی) دیاری نەکراوە
            throw new BadRequestException("project.kmr_type_required", Map.of());
        }

        if (dto.getContentLanguages() == null || dto.getContentLanguages().isEmpty()) {
            // هەڵە: هیچ زمانێک دیاری نەکراوە بۆ پرۆژەکە
            throw new BadRequestException("project.languages_required", Map.of());
        }
        if (requireCoverUrl && isBlank(dto.getCoverUrl())) {
            // هەڵە: لینکی وێنەی بەرگ نەنێردراوە
            throw new BadRequestException("project.cover_required", Map.of());
        }
        if (hasCkbLang && (dto.getCkbContent() == null || isBlank(dto.getCkbContent().getTitle()))) {
            // هەڵە: ناونیشان بە کوردیی ناوەندی پێویستە
            throw new BadRequestException("project.ckb_title_required", Map.of());
        }
        if (hasKmrLang && (dto.getKmrContent() == null || isBlank(dto.getKmrContent().getTitle()))) {
            // هەڵە: ناونیشان بە کوردیی باکووری پێویستە
            throw new BadRequestException("project.kmr_title_required", Map.of());
        }
    }

    // ============================================================
    // بەڕێوەبردنی پەیوەندییەکان
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
    // بەڕێوەبردنی میدیا
    // ============================================================
    /**
     * لکاندنی میدیا لە DTO
     *
     * هەڵەکانی میدیا بە کوردی:
     * - "media.type_invalid" = جۆری میدیا هەڵەیە: جۆرەکە ناناسرێتەوە
     * - "media.audio_video_requires_url_or_link" = ئۆدیۆ/ڤیدیۆ پێویستی بە لینکی ڕاستەقینە یان لینکی دەرەکی یان ئێمبێدە
     * - "media.url_or_text_required" = بۆ جۆرەکانی دیکە، لینکی ڕاستەقینە یان دەقی پێناسە پێویستە
     */
    private void attachMediaFromDto(Project project, List<ProjectMediaCreateRequest> mediaList) {
        if (mediaList == null || mediaList.isEmpty()) return;

        for (ProjectMediaCreateRequest m : mediaList) {
            if (m == null) continue;

            ProjectMediaType type;
            try {
                type = ProjectMediaType.valueOf(m.getMediaType());
            } catch (IllegalArgumentException ex) {
                // هەڵە: جۆری میدیا نەناسراوە (ئایمێج، ڤیدیۆ، ئۆدیۆ، پی دی ئێف، دۆکیومێنت)
                throw new BadRequestException("media.type_invalid",
                        Map.of("mediaType", safe(m.getMediaType())));
            }

            boolean hasUrl      = !isBlank(m.getUrl());
            boolean hasExternal = !isBlank(m.getExternalUrl());
            boolean hasEmbed    = !isBlank(m.getEmbedUrl());
            boolean hasText     = !isBlank(m.getTextBody());

            if (type == ProjectMediaType.AUDIO || type == ProjectMediaType.VIDEO) {
                if (!hasUrl && !hasExternal && !hasEmbed) {
                    // هەڵە: بۆ ئۆدیۆ یان ڤیدیۆ دەبێت لینکی ڕاستەقینە یان لینکی دەرەکی یان کۆدی ئێمبێد هەبێت
                    throw new BadRequestException("media.audio_video_requires_url_or_link",
                            Map.of("mediaType", type.name()));
                }
            } else {
                if (!hasUrl && !hasText) {
                    // هەڵە: بۆ وێنە و فایلی دیکە دەبێت لینک یان دەقی پێناسە هەبێت
                    throw new BadRequestException("media.url_or_text_required",
                            Map.of("mediaType", type.name()));
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
    // گۆڕینی بۆ Response
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
                .projectTypeCkb(project.getProjectTypeCkb())
                .projectTypeKmr(project.getProjectTypeKmr())
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
    // تۆمارکردنی چالاکی (Audit Log)
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
            log.warn("شکستی تۆمارکردنی چۆنێتیی پرۆژە | projectId={} | action={}",
                    project != null ? project.getId() : null, action, e);
        }
    }

    // ============================================================
    // یاریدەدەرەکانی گشتی
    // ============================================================

    /**
     * دۆزینەوەی پرۆژە یان هەڵەدان
     *
     * @throws NotFoundException - ئەگەر پرۆژەکە نەدۆزرێتەوە ("پرۆژەکە نەدۆزرایەوە")
     */
    private Project findOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException(
                        "project.not_found", Map.of("id", safe(projectId))));
    }

    /**
     * ناسینەوەی جۆری میدیا لە فایل
     */
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
        return "پرۆژە#" + p.getId();
    }

    private String normKey(String raw) { return safe(raw).trim().toLowerCase(); }

    private record UploadedMedia(ProjectMediaType mediaType, String url, String caption, int sortOrder) {}

}