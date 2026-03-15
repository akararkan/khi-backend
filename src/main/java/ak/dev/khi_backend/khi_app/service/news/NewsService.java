package ak.dev.khi_backend.khi_app.service.news;

import ak.dev.khi_backend.khi_app.dto.news.NewsDto;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.news.NewsMediaType;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.exceptions.NotFoundException;
import ak.dev.khi_backend.khi_app.model.news.*;
import ak.dev.khi_backend.khi_app.repository.news.NewsAuditLogRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsCategoryRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsSubCategoryRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepository             newsRepository;
    private final NewsCategoryRepository     newsCategoryRepository;
    private final NewsSubCategoryRepository  newsSubCategoryRepository;
    private final NewsAuditLogRepository     newsAuditLogRepository;
    private final S3Service                  s3Service;
    private final TransactionTemplate        transactionTemplate;

    // ============================================================
    // دروستکردن (لەگەڵ فایل)
    // ============================================================

    @CacheEvict(value = "news", allEntries = true)
    public NewsDto addNews(NewsDto dto,
                           MultipartFile coverImage,
                           List<MultipartFile> mediaFiles) {
        String traceId   = traceId();
        boolean hasCover = hasFile(coverImage);
        boolean hasMedia = hasFiles(mediaFiles);
        log.info("دروستکردنی هەواڵ | traceId={}", traceId);

        validate(dto, true, hasCover);

        int mediaCount = mediaFiles != null ? mediaFiles.size() : 0;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, Math.max(2, 1 + mediaCount)));

        try {
            // ── S3 uploads (parallel, outside transaction) ──────────
            CompletableFuture<String> coverFuture =
                    CompletableFuture.completedFuture(trimOrNull(dto.getCoverUrl()));

            if (hasCover) {
                coverFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return s3Service.upload(
                                coverBytes(coverImage),
                                coverImage.getOriginalFilename(),
                                coverImage.getContentType());
                    } catch (IOException e) {
                        throw new CompletionException("کێشە لە ناردنی وێنەی بەرگ", e);
                    }
                }, pool);
            }

            List<CompletableFuture<UploadedMedia>> mediaFutures =
                    buildMediaFutures(mediaFiles, pool);

            String coverUrl = trimOrNull(coverFuture.join());
            if (isBlank(coverUrl)) {
                throw new BadRequestException("news.cover.required",
                        Map.of("field", "coverImage_or_coverUrl", "traceId", traceId));
            }

            List<UploadedMedia> uploaded = joinMediaFutures(mediaFutures);

            // ── DB transaction ───────────────────────────────────────
            News saved = transactionTemplate.execute(status -> {
                NewsCategory    cat    = getOrCreateCategory(dto.getCategory());
                NewsSubCategory subCat = getOrCreateSubCategory(dto.getSubCategory(), cat);

                News news = buildNewsEntity(dto, coverUrl, cat, subCat);
                applyContentByLanguages(news, dto);

                if (!uploaded.isEmpty()) {
                    appendUploadedMedia(news, uploaded);
                } else {
                    attachMediaFromDto(news, dto.getMedia());
                }

                News p = newsRepository.save(news);
                createAuditLog(p, "CREATE", "هەواڵ دروستکرا");
                return p;
            });

            // force collection init inside session boundary
            return toDto(saved);

        } catch (CompletionException ex) {
            Throwable root = ex.getCause() != null ? ex.getCause() : ex;
            if (root instanceof IOException) {
                throw new BadRequestException("media.upload.failed", Map.of("traceId", traceId));
            }
            throw ex;
        } finally {
            pool.shutdown();
        }
    }

    // ============================================================
    // دروستکردنی بە کۆمەڵ (JSON)
    // ============================================================

    @CacheEvict(value = "news", allEntries = true)
    public List<NewsDto> addNewsBulk(List<NewsDto> list) {
        if (list == null || list.isEmpty()) {
            throw new BadRequestException("error.validation",
                    Map.of("field", "list", "message", "لیستی هەواڵەکان بەتاڵە"));
        }
        for (NewsDto dto : list) {
            validate(dto, false, false);
            if (isBlank(dto.getCoverUrl())) {
                throw new BadRequestException("news.coverUrl.required",
                        Map.of("field", "coverUrl"));
            }
        }

        List<News> saved = transactionTemplate.execute(status -> {
            List<News> entities = new ArrayList<>(list.size());
            for (NewsDto dto : list) {
                NewsCategory    cat    = getOrCreateCategory(dto.getCategory());
                NewsSubCategory subCat = getOrCreateSubCategory(dto.getSubCategory(), cat);
                News news = buildNewsEntity(dto, dto.getCoverUrl().trim(), cat, subCat);
                applyContentByLanguages(news, dto);
                attachMediaFromDto(news, dto.getMedia());
                entities.add(news);
            }
            List<News> out = newsRepository.saveAll(entities);
            newsAuditLogRepository.saveAll(
                    out.stream()
                            .map(n -> buildAuditLog(n, "CREATE", "هەواڵ بە کۆمەڵ دروستکرا"))
                            .toList()
            );
            return out;
        });

        return saved.stream().map(this::toDto).toList();
    }

    // ============================================================
    // GET ALL — Paginated + Cached
    //
    // Execution plan for page of 20:
    //   Q1: SELECT id FROM news ORDER BY date DESC  (Phase 1 — index scan)
    //   Q2: SELECT n  FROM news WHERE id IN (...)   (Phase 2 — bare rows)
    //   Q3-Q9: @BatchSize fires 1 IN-query per collection type
    //   Total: ~9 fast queries. Cache hit: <5ms.
    // ============================================================

    @Cacheable(value = "news", key = "'all:p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<NewsDto> getAllNews(int page, int size) {
        Page<Long> idPage = newsRepository.findAllIds(PageRequest.of(page, size));

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }

        List<News> hydrated = hydrateAndSort(idPage.getContent());

        return new PageImpl<>(
                hydrated.stream().map(this::toDto).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    // ============================================================
    // SEARCH BY TAG — Paginated + Cached
    //
    // language: "ckb" | "kmr" | null/empty = both
    // ============================================================

    @Cacheable(value = "news",
            key = "'tag:' + #tag.toLowerCase() + ':lang:' + #language + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<NewsDto> searchByTag(String tag, String language, int page, int size) {
        if (isBlank(tag)) {
            throw new BadRequestException("tag.required",
                    Map.of("message", "تاگی گەڕان پێویستە"));
        }

        Pageable pageable = PageRequest.of(page, size);
        String   t        = tag.trim();

        Page<Long> idPage;
        if ("ckb".equalsIgnoreCase(language)) {
            idPage = newsRepository.findIdsByTagCkb(t, pageable);
        } else if ("kmr".equalsIgnoreCase(language)) {
            idPage = newsRepository.findIdsByTagKmr(t, pageable);
        } else {
            idPage = newsRepository.findIdsByTag(t, pageable);
        }

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }

        List<News> hydrated = hydrateAndSort(idPage.getContent());

        return new PageImpl<>(
                hydrated.stream().map(this::toDto).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    // ============================================================
    // SEARCH BY KEYWORD — Paginated + Cached
    // ============================================================

    @Cacheable(value = "news",
            key = "'kw:' + #keyword.toLowerCase() + ':lang:' + #language + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<NewsDto> searchByKeyword(String keyword, String language, int page, int size) {
        if (isBlank(keyword)) {
            throw new BadRequestException("keyword.required",
                    Map.of("message", "کلیلەووشەی گەڕان بەتاڵە"));
        }

        Pageable pageable = PageRequest.of(page, size);
        String   kw       = keyword.trim();

        Page<Long> idPage;
        if ("ckb".equalsIgnoreCase(language)) {
            idPage = newsRepository.findIdsByKeywordCkb(kw, pageable);
        } else if ("kmr".equalsIgnoreCase(language)) {
            idPage = newsRepository.findIdsByKeywordKmr(kw, pageable);
        } else {
            idPage = newsRepository.findIdsByKeyword(kw, pageable);
        }

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }

        List<News> hydrated = hydrateAndSort(idPage.getContent());

        return new PageImpl<>(
                hydrated.stream().map(this::toDto).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    // ============================================================
    // GLOBAL SEARCH — Modern "one search box"
    // Searches title + description + tags + keywords, both languages
    // ============================================================

    @Cacheable(value = "news",
            key = "'search:' + #q.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<NewsDto> globalSearch(String q, int page, int size) {
        if (isBlank(q)) {
            throw new BadRequestException("keyword.required",
                    Map.of("message", "کلیلەووشەی گەڕان پێویستە"));
        }

        Page<Long> idPage = newsRepository.findIdsByGlobalSearch(
                q.trim(), PageRequest.of(page, size));

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }

        List<News> hydrated = hydrateAndSort(idPage.getContent());

        return new PageImpl<>(
                hydrated.stream().map(this::toDto).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    // ============================================================
    // SEARCH BY CATEGORY / SUBCATEGORY — Paginated + Cached
    // ============================================================

    @Cacheable(value = "news",
            key = "'cat:' + #name.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<NewsDto> searchByCategory(String name, int page, int size) {
        if (isBlank(name)) {
            throw new BadRequestException("news.category.required",
                    Map.of("field", "category"));
        }

        Page<Long> idPage = newsRepository.findIdsByCategory(
                name.trim(), PageRequest.of(page, size));

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }

        List<News> hydrated = hydrateAndSort(idPage.getContent());

        return new PageImpl<>(
                hydrated.stream().map(this::toDto).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    @Cacheable(value = "news",
            key = "'subcat:' + #name.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<NewsDto> searchBySubCategory(String name, int page, int size) {
        if (isBlank(name)) {
            throw new BadRequestException("news.subcategory.required",
                    Map.of("field", "subCategory"));
        }

        Page<Long> idPage = newsRepository.findIdsBySubCategory(
                name.trim(), PageRequest.of(page, size));

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }

        List<News> hydrated = hydrateAndSort(idPage.getContent());

        return new PageImpl<>(
                hydrated.stream().map(this::toDto).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    // ============================================================
    // GET BY ID
    // ============================================================

    @Transactional(readOnly = true)
    public NewsDto getNewsById(Long id) {
        News news = newsRepository.findByIdWithGraph(id)
                .orElseThrow(() -> new NotFoundException(
                        "news.not_found", Map.of("id", id)));
        return toDto(news);
    }

    // ============================================================
    // نوێکردنەوە (لەگەڵ فایل)
    // ============================================================

    @CacheEvict(value = "news", allEntries = true)
    public NewsDto updateNews(Long newsId,
                              NewsDto dto,
                              MultipartFile newCoverImage,
                              List<MultipartFile> newMediaFiles) {
        String traceId       = traceId();
        boolean hasNewCover  = hasFile(newCoverImage);
        boolean hasNewMedia  = hasFiles(newMediaFiles);
        log.info("نوێکردنەوەی هەواڵ | id={} | traceId={}", newsId, traceId);

        if (newsId == null) {
            throw new BadRequestException("error.validation",
                    Map.of("field", "id", "message", "ئایدیی هەواڵ پێویستە"));
        }

        validate(dto, false, false);

        int mediaCount = newMediaFiles != null ? newMediaFiles.size() : 0;
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(8, Math.max(2, 1 + mediaCount)));

        try {
            CompletableFuture<String> coverFuture =
                    CompletableFuture.completedFuture(null);

            if (hasNewCover) {
                coverFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return s3Service.upload(
                                coverBytes(newCoverImage),
                                newCoverImage.getOriginalFilename(),
                                newCoverImage.getContentType());
                    } catch (IOException e) {
                        throw new CompletionException("کێشە لە ناردنی وێنەی بەرگی نوێ", e);
                    }
                }, pool);
            }

            List<CompletableFuture<UploadedMedia>> mediaFutures =
                    buildMediaFutures(newMediaFiles, pool);

            String uploadedCoverUrl = trimOrNull(coverFuture.join());
            List<UploadedMedia> uploaded = joinMediaFutures(mediaFutures);

            News updated = transactionTemplate.execute(status -> {
                News news = newsRepository.findByIdWithGraph(newsId)
                        .orElseThrow(() -> new BadRequestException(
                                "news.not_found", Map.of("id", newsId)));

                // Cover logic — never allow null result
                if (!isBlank(uploadedCoverUrl)) {
                    news.setCoverUrl(uploadedCoverUrl);
                } else if (!isBlank(dto.getCoverUrl())) {
                    news.setCoverUrl(dto.getCoverUrl().trim());
                } else if (isBlank(news.getCoverUrl())) {
                    throw new BadRequestException("news.cover.required",
                            Map.of("field", "coverImage_or_coverUrl"));
                }

                if (dto.getDatePublished() != null) {
                    news.setDatePublished(dto.getDatePublished());
                }

                if (dto.getCategory() != null) {
                    news.setCategory(getOrCreateCategory(dto.getCategory()));
                }
                if (dto.getSubCategory() != null) {
                    news.setSubCategory(
                            getOrCreateSubCategory(dto.getSubCategory(), news.getCategory()));
                }

                news.setContentLanguages(new LinkedHashSet<>(safeLangs(dto.getContentLanguages())));
                applyContentByLanguages(news, dto);
                replaceBilingualSets(news, dto);

                // Media logic
                if (!uploaded.isEmpty()) {
                    news.getMedia().clear();
                    appendUploadedMedia(news, uploaded);
                } else if (dto.getMedia() != null) {
                    news.getMedia().clear();
                    attachMediaFromDto(news, dto.getMedia());
                }
                // else: leave media unchanged

                News saved = newsRepository.save(news);
                createAuditLog(saved, "UPDATE", "هەواڵ نوێکرایەوە");
                return saved;
            });

            return toDto(updated);

        } catch (CompletionException ex) {
            Throwable root = ex.getCause() != null ? ex.getCause() : ex;
            if (root instanceof IOException) {
                throw new BadRequestException("media.upload.failed",
                        Map.of("traceId", traceId));
            }
            throw ex;
        } finally {
            pool.shutdown();
        }
    }

    // ============================================================
    // سڕینەوە
    // ============================================================

    @CacheEvict(value = "news", allEntries = true)
    public void deleteNews(Long newsId) {
        if (newsId == null) {
            throw new BadRequestException("error.validation",
                    Map.of("field", "id", "message", "ئایدیی هەواڵ بۆ سڕینەوە پێویستە"));
        }
        transactionTemplate.executeWithoutResult(status -> {
            News news = newsRepository.findByIdWithGraph(newsId)
                    .orElseThrow(() -> new BadRequestException(
                            "news.not_found", Map.of("id", newsId)));
            createAuditLog(news, "DELETE", "هەواڵ سڕایەوە");
            newsRepository.delete(news);
        });
    }

    @CacheEvict(value = "news", allEntries = true)
    public void deleteNewsBulk(List<Long> newsIds) {
        if (newsIds == null || newsIds.isEmpty()) {
            throw new BadRequestException("error.validation",
                    Map.of("field", "newsIds", "message", "لیستی ئایدیەکان بەتاڵە"));
        }
        transactionTemplate.executeWithoutResult(status -> {
            List<News> list = newsRepository.findAllById(newsIds);
            if (list.isEmpty()) {
                throw new BadRequestException("news.not_found",
                        Map.of("ids", newsIds));
            }
            newsAuditLogRepository.saveAll(
                    list.stream()
                            .map(n -> buildAuditLog(n, "DELETE", "هەواڵ بە کۆمەڵ سڕایەوە"))
                            .toList()
            );
            newsRepository.deleteAll(list);
        });
    }

    // ============================================================
    // CORE HYDRATION HELPER
    //
    // Step 1: fetch bare News rows              → 1 query
    // Step 2: @BatchSize fires automatically    → 1 IN-query per collection
    // Step 3: re-order to match Phase-1 pagination order
    // ============================================================

    private List<News> hydrateAndSort(List<Long> ids) {
        List<News> rows = newsRepository.findAllByIds(ids);

        Map<Long, News> indexed = new LinkedHashMap<>(rows.size());
        for (News n : rows) indexed.put(n.getId(), n);

        List<News> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            News n = indexed.get(id);
            if (n != null) ordered.add(n);
        }
        return ordered;
    }

    // ============================================================
    // ENTITY BUILDER
    // ============================================================

    private News buildNewsEntity(NewsDto dto, String coverUrl,
                                 NewsCategory cat, NewsSubCategory subCat) {
        return News.builder()
                .coverUrl(coverUrl)
                .datePublished(dto.getDatePublished())
                .category(cat)
                .subCategory(subCat)
                .contentLanguages(new LinkedHashSet<>(safeLangs(dto.getContentLanguages())))
                .tagsCkb(new LinkedHashSet<>(cleanStrings(
                        dto.getTags() != null ? dto.getTags().getCkb() : null)))
                .tagsKmr(new LinkedHashSet<>(cleanStrings(
                        dto.getTags() != null ? dto.getTags().getKmr() : null)))
                .keywordsCkb(new LinkedHashSet<>(cleanStrings(
                        dto.getKeywords() != null ? dto.getKeywords().getCkb() : null)))
                .keywordsKmr(new LinkedHashSet<>(cleanStrings(
                        dto.getKeywords() != null ? dto.getKeywords().getKmr() : null)))
                .build();
    }

    // ============================================================
    // پشتڕاستکردنەوە
    // ============================================================

    private void validate(NewsDto dto, boolean createRequiresCover, boolean hasCoverFile) {
        if (dto == null) {
            throw new BadRequestException("error.validation",
                    Map.of("field", "body", "message", "داواکاری پێویستە"));
        }

        Set<Language> langs = safeLangs(dto.getContentLanguages());
        if (langs.isEmpty()) {
            throw new BadRequestException("news.languages.required",
                    Map.of("field", "contentLanguages"));
        }

        if (createRequiresCover && !hasCoverFile && isBlank(dto.getCoverUrl())) {
            throw new BadRequestException("news.cover.required",
                    Map.of("field", "coverImage_or_coverUrl"));
        }

        if (dto.getCategory() == null
                || isBlank(dto.getCategory().getCkbName())
                || isBlank(dto.getCategory().getKmrName())) {
            throw new BadRequestException("news.category.required",
                    Map.of("field", "category"));
        }
        if (dto.getSubCategory() == null
                || isBlank(dto.getSubCategory().getCkbName())
                || isBlank(dto.getSubCategory().getKmrName())) {
            throw new BadRequestException("news.subcategory.required",
                    Map.of("field", "subCategory"));
        }

        if (langs.contains(Language.CKB)) {
            if (dto.getCkbContent() == null || isBlank(dto.getCkbContent().getTitle())) {
                throw new BadRequestException("news.ckb.title.required",
                        Map.of("field", "ckbContent.title"));
            }
        }
        if (langs.contains(Language.KMR)) {
            if (dto.getKmrContent() == null || isBlank(dto.getKmrContent().getTitle())) {
                throw new BadRequestException("news.kmr.title.required",
                        Map.of("field", "kmrContent.title"));
            }
        }
    }

    // ============================================================
    // هاوپۆل و هاوپۆلی فرعی
    // ============================================================

    private NewsCategory getOrCreateCategory(NewsDto.CategoryDto categoryDto) {
        String ckb = trimOrNull(categoryDto != null ? categoryDto.getCkbName() : null);
        String kmr = trimOrNull(categoryDto != null ? categoryDto.getKmrName() : null);

        if (isBlank(ckb) || isBlank(kmr)) {
            throw new BadRequestException("news.category.required",
                    Map.of("field", "category"));
        }

        NewsCategory existing = newsCategoryRepository.findByNameCkb(ckb).orElse(null);
        if (existing != null) {
            if (!kmr.equals(existing.getNameKmr())) {
                existing.setNameKmr(kmr);
                existing = newsCategoryRepository.save(existing);
            }
            return existing;
        }

        return newsCategoryRepository.save(
                NewsCategory.builder().nameCkb(ckb).nameKmr(kmr).build());
    }

    private NewsSubCategory getOrCreateSubCategory(NewsDto.SubCategoryDto subDto,
                                                   NewsCategory category) {
        String ckb = trimOrNull(subDto != null ? subDto.getCkbName() : null);
        String kmr = trimOrNull(subDto != null ? subDto.getKmrName() : null);

        if (isBlank(ckb) || isBlank(kmr)) {
            throw new BadRequestException("news.subcategory.required",
                    Map.of("field", "subCategory"));
        }

        NewsSubCategory existing =
                newsSubCategoryRepository.findByCategoryAndNameCkb(category, ckb).orElse(null);
        if (existing != null) {
            if (!kmr.equals(existing.getNameKmr())) {
                existing.setNameKmr(kmr);
                existing = newsSubCategoryRepository.save(existing);
            }
            return existing;
        }

        return newsSubCategoryRepository.save(
                NewsSubCategory.builder()
                        .nameCkb(ckb).nameKmr(kmr).category(category)
                        .build());
    }

    // ============================================================
    // ناوەڕۆک
    // ============================================================

    private void applyContentByLanguages(News news, NewsDto dto) {
        Set<Language> langs = safeLangs(news.getContentLanguages());

        if (langs.contains(Language.CKB)) {
            news.setCkbContent(buildContent(dto.getCkbContent()));
        } else {
            news.setCkbContent(null);
            news.getTagsCkb().clear();
            news.getKeywordsCkb().clear();
        }

        if (langs.contains(Language.KMR)) {
            news.setKmrContent(buildContent(dto.getKmrContent()));
        } else {
            news.setKmrContent(null);
            news.getTagsKmr().clear();
            news.getKeywordsKmr().clear();
        }
    }

    private NewsContent buildContent(NewsDto.LanguageContentDto dto) {
        if (dto == null) return null;
        if (isBlank(dto.getTitle()) && isBlank(dto.getDescription())) return null;
        return NewsContent.builder()
                .title(trimOrNull(dto.getTitle()))
                .description(trimOrNull(dto.getDescription()))
                .build();
    }

    private void replaceBilingualSets(News news, NewsDto dto) {
        if (dto.getTags() != null) {
            if (dto.getTags().getCkb() != null) {
                news.getTagsCkb().clear();
                news.getTagsCkb().addAll(cleanStrings(dto.getTags().getCkb()));
            }
            if (dto.getTags().getKmr() != null) {
                news.getTagsKmr().clear();
                news.getTagsKmr().addAll(cleanStrings(dto.getTags().getKmr()));
            }
        }
        if (dto.getKeywords() != null) {
            if (dto.getKeywords().getCkb() != null) {
                news.getKeywordsCkb().clear();
                news.getKeywordsCkb().addAll(cleanStrings(dto.getKeywords().getCkb()));
            }
            if (dto.getKeywords().getKmr() != null) {
                news.getKeywordsKmr().clear();
                news.getKeywordsKmr().addAll(cleanStrings(dto.getKeywords().getKmr()));
            }
        }
    }

    // ============================================================
    // میدیا
    // ============================================================

    private void attachMediaFromDto(News news, List<NewsDto.MediaDto> mediaDtos) {
        if (mediaDtos == null || mediaDtos.isEmpty()) return;

        Set<String> existing = new HashSet<>();
        for (NewsMedia m : news.getMedia()) {
            String key = mediaKey(m.getType(), m.getUrl(), m.getEmbedUrl(), m.getExternalUrl());
            if (key != null) existing.add(key);
        }

        for (NewsDto.MediaDto m : mediaDtos) {
            if (m == null || m.getType() == null) continue;

            String url         = trimOrNull(m.getUrl());
            String externalUrl = trimOrNull(m.getExternalUrl());
            String embedUrl    = trimOrNull(m.getEmbedUrl());

            boolean hasUrl      = !isBlank(url);
            boolean hasExternal = !isBlank(externalUrl);
            boolean hasEmbed    = !isBlank(embedUrl);

            if (m.getType() == NewsMediaType.AUDIO || m.getType() == NewsMediaType.VIDEO) {
                if (!hasUrl && !hasExternal && !hasEmbed) {
                    throw new BadRequestException(
                            "news.media.audio_video_requires_url_or_link",
                            Map.of("type", m.getType().name()));
                }
            } else {
                if (!hasUrl) {
                    throw new BadRequestException("news.media.url_required",
                            Map.of("type", m.getType().name()));
                }
            }

            String key = mediaKey(m.getType(), url, embedUrl, externalUrl);
            if (key == null || existing.contains(key)) continue;

            news.getMedia().add(NewsMedia.builder()
                    .news(news)
                    .type(m.getType())
                    .url(url)
                    .externalUrl(externalUrl)
                    .embedUrl(embedUrl)
                    .sortOrder(m.getSortOrder() != null ? m.getSortOrder() : 0)
                    .build());

            existing.add(key);
        }
    }

    private void appendUploadedMedia(News news, List<UploadedMedia> uploaded) {
        for (UploadedMedia um : uploaded) {
            news.getMedia().add(NewsMedia.builder()
                    .news(news)
                    .type(um.type())
                    .url(um.url())
                    .sortOrder(um.sortOrder())
                    .build());
        }
    }

    private String mediaKey(NewsMediaType type, String url, String embedUrl, String ext) {
        if (type == null) return null;
        String best = trimOrNull(url);
        if (best == null) best = trimOrNull(embedUrl);
        if (best == null) best = trimOrNull(ext);
        if (best == null) return null;
        return type.name() + "|" + best;
    }

    private NewsMediaType detectMediaType(String contentType) {
        if (contentType == null) return NewsMediaType.DOCUMENT;
        String t = contentType.toLowerCase();
        if (t.startsWith("image/")) return NewsMediaType.IMAGE;
        if (t.startsWith("video/")) return NewsMediaType.VIDEO;
        if (t.startsWith("audio/")) return NewsMediaType.AUDIO;
        return NewsMediaType.DOCUMENT;
    }

    // ============================================================
    // S3 helpers
    // ============================================================

    private List<CompletableFuture<UploadedMedia>> buildMediaFutures(
            List<MultipartFile> files, ExecutorService pool) {
        List<CompletableFuture<UploadedMedia>> futures = new ArrayList<>();
        if (files == null || files.isEmpty()) return futures;
        int sort = 0;
        for (MultipartFile f : files) {
            if (!hasFile(f)) continue;
            final int so = sort++;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    String url = s3Service.upload(
                            f.getBytes(), f.getOriginalFilename(), f.getContentType());
                    return new UploadedMedia(detectMediaType(f.getContentType()), url, so);
                } catch (IOException e) {
                    throw new CompletionException(
                            "کێشە لە ناردنی فایلی میدیا: " + f.getOriginalFilename(), e);
                }
            }, pool));
        }
        return futures;
    }

    private List<UploadedMedia> joinMediaFutures(
            List<CompletableFuture<UploadedMedia>> futures) {
        List<UploadedMedia> result = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<UploadedMedia> f : futures) result.add(f.join());
        } catch (CompletionException ex) {
            Throwable root = ex.getCause() != null ? ex.getCause() : ex;
            if (root instanceof RuntimeException re) throw re;
            throw ex;
        }
        return result;
    }

    // ============================================================
    // گۆڕین بۆ DTO
    //
    // CRITICAL: every collection access here happens inside
    // an open @Transactional session → @BatchSize fires and loads.
    // All collections copied into plain Java types (new LinkedHashSet,
    // new ArrayList) before the session closes → Jackson can
    // serialize them safely after the transaction ends.
    // ============================================================

    private NewsDto toDto(News news) {
        NewsDto dto = NewsDto.builder()
                .id(news.getId())
                .coverUrl(news.getCoverUrl())
                .datePublished(news.getDatePublished())
                .createdAt(news.getCreatedAt())
                .updatedAt(news.getUpdatedAt())
                // ← copy into plain Set — prevents LazyInitializationException
                .contentLanguages(news.getContentLanguages() != null
                        ? new LinkedHashSet<>(news.getContentLanguages())
                        : new LinkedHashSet<>())
                .build();

        if (news.getCategory() != null) {
            dto.setCategory(NewsDto.CategoryDto.builder()
                    .ckbName(news.getCategory().getNameCkb())
                    .kmrName(news.getCategory().getNameKmr())
                    .build());
        }

        if (news.getSubCategory() != null) {
            dto.setSubCategory(NewsDto.SubCategoryDto.builder()
                    .ckbName(news.getSubCategory().getNameCkb())
                    .kmrName(news.getSubCategory().getNameKmr())
                    .build());
        }

        if (news.getCkbContent() != null) {
            dto.setCkbContent(NewsDto.LanguageContentDto.builder()
                    .title(news.getCkbContent().getTitle())
                    .description(news.getCkbContent().getDescription())
                    .build());
        }

        if (news.getKmrContent() != null) {
            dto.setKmrContent(NewsDto.LanguageContentDto.builder()
                    .title(news.getKmrContent().getTitle())
                    .description(news.getKmrContent().getDescription())
                    .build());
        }

        // ← copy into plain Set — prevents LazyInitializationException
        dto.setTags(NewsDto.BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(news.getTagsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(news.getTagsKmr())))
                .build());

        dto.setKeywords(NewsDto.BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(news.getKeywordsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(news.getKeywordsKmr())))
                .build());

        if (news.getMedia() != null && !news.getMedia().isEmpty()) {
            dto.setMedia(
                    new ArrayList<>(news.getMedia()).stream()
                            .sorted(Comparator.comparingInt(
                                    m -> m.getSortOrder() != null ? m.getSortOrder() : 0))
                            .map(m -> NewsDto.MediaDto.builder()
                                    .id(m.getId())
                                    .type(m.getType())
                                    .url(m.getUrl())
                                    .externalUrl(m.getExternalUrl())
                                    .embedUrl(m.getEmbedUrl())
                                    .sortOrder(m.getSortOrder())
                                    .createdAt(m.getCreatedAt())
                                    .build())
                            .collect(Collectors.toList())
            );
        } else {
            dto.setMedia(List.of());
        }

        return dto;
    }

    // ============================================================
    // تۆمارکردنی چالاکی
    // ============================================================

    private void createAuditLog(News news, String action, String note) {
        newsAuditLogRepository.save(buildAuditLog(news, action, note));
    }

    private NewsAuditLog buildAuditLog(News news, String action, String note) {
        return NewsAuditLog.builder()
                .newsId(news.getId())
                .action(action)
                .performedBy("system")
                .note(note)
                .build();
    }

    // ============================================================
    // یاریدەدەرەکان
    // ============================================================

    private boolean hasFile(MultipartFile f)         { return f != null && !f.isEmpty(); }
    private boolean isBlank(String s)                { return s == null || s.isBlank(); }
    private String  trimOrNull(String s)             { if (s == null) return null; String t = s.trim(); return t.isEmpty() ? null : t; }
    private Set<Language>  safeLangs(Set<Language> l){ return l == null ? Set.of() : l; }
    private <T> Set<T>     safeSet(Set<T> s)         { return s == null ? Set.of() : s; }

    private boolean hasFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return false;
        for (MultipartFile f : files) { if (hasFile(f)) return true; }
        return false;
    }

    private byte[] coverBytes(MultipartFile f) throws IOException {
        if (f == null || f.isEmpty()) {
            throw new BadRequestException("news.cover.required", Map.of("field", "cover"));
        }
        return f.getBytes();
    }

    private Set<String> cleanStrings(Set<String> input) {
        if (input == null || input.isEmpty()) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : input) { if (s != null && !s.isBlank()) out.add(s.trim()); }
        return out;
    }

    private String traceId() {
        String t = MDC.get("traceId");
        return (t != null && !t.isBlank()) ? t : UUID.randomUUID().toString();
    }

    private record UploadedMedia(NewsMediaType type, String url, int sortOrder) {}
}