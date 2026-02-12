package ak.dev.khi_backend.khi_app.service.news;

import ak.dev.khi_backend.khi_app.dto.news.NewsDto;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.news.NewsMediaType;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.model.news.*;
import ak.dev.khi_backend.khi_app.repository.news.NewsAuditLogRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsCategoryRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsSubCategoryRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepository newsRepository;
    private final NewsCategoryRepository newsCategoryRepository;
    private final NewsSubCategoryRepository newsSubCategoryRepository;
    private final NewsAuditLogRepository newsAuditLogRepository;
    private final S3Service s3Service;
    private final TransactionTemplate transactionTemplate;

    // ============================================================
    // CREATE (multipart: cover + mediaFiles)
    // ============================================================

    public NewsDto addNews(NewsDto dto, MultipartFile coverImage, List<MultipartFile> mediaFiles) {
        String traceId = MDC.get("traceId");
        log.info("Creating news with files | traceId={}", traceId);

        validate(dto, true, coverImage);

        int mediaCount = mediaFiles != null ? mediaFiles.size() : 0;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, Math.max(2, 1 + mediaCount)));

        try {
            // ---------- Upload outside transaction ----------
            CompletableFuture<String> coverFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return s3Service.upload(coverBytes(coverImage), coverImage.getOriginalFilename(), coverImage.getContentType());
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, pool);

            List<CompletableFuture<UploadedMedia>> mediaFutures = new ArrayList<>();
            if (mediaFiles != null && !mediaFiles.isEmpty()) {
                int sort = 0;
                for (MultipartFile f : mediaFiles) {
                    if (f == null || f.isEmpty()) continue;
                    final int so = sort++;
                    mediaFutures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            String url = s3Service.upload(f.getBytes(), f.getOriginalFilename(), f.getContentType());
                            return new UploadedMedia(detectMediaType(f.getContentType()), url, so);
                        } catch (IOException e) {
                            throw new CompletionException(e);
                        }
                    }, pool));
                }
            }

            String coverUrl = coverFuture.join();
            List<UploadedMedia> uploadedMedia = new ArrayList<>();
            for (CompletableFuture<UploadedMedia> f : mediaFutures) uploadedMedia.add(f.join());

            // ---------- DB transaction ----------
            News saved = transactionTemplate.execute(status -> {
                NewsCategory category = getOrCreateCategory(dto.getCategory());
                NewsSubCategory subCategory = getOrCreateSubCategory(dto.getSubCategory(), category);

                News news = News.builder()
                        .coverUrl(coverUrl)
                        .datePublished(dto.getDatePublished())
                        .category(category)
                        .subCategory(subCategory)
                        .contentLanguages(new LinkedHashSet<>(safeLangs(dto.getContentLanguages())))
                        .tagsCkb(new LinkedHashSet<>(safeSet(dto.getTags() != null ? dto.getTags().getCkb() : null)))
                        .tagsKmr(new LinkedHashSet<>(safeSet(dto.getTags() != null ? dto.getTags().getKmr() : null)))
                        .keywordsCkb(new LinkedHashSet<>(safeSet(dto.getKeywords() != null ? dto.getKeywords().getCkb() : null)))
                        .keywordsKmr(new LinkedHashSet<>(safeSet(dto.getKeywords() != null ? dto.getKeywords().getKmr() : null)))
                        .build();

                applyContentByLanguages(news, dto);

                // media from uploaded files
                if (!uploadedMedia.isEmpty()) {
                    for (UploadedMedia um : uploadedMedia) {
                        news.getMedia().add(NewsMedia.builder()
                                .news(news)
                                .type(um.type())
                                .url(um.url())
                                .externalUrl(null)
                                .embedUrl(null)
                                .sortOrder(um.sortOrder())
                                .build());
                    }
                }

                // media from dto URLs (optional)  ✅ upgraded
                attachMediaFromDto(news, dto.getMedia());

                News p = newsRepository.save(news);
                createAuditLog(p, "CREATE", "news.created");
                return p;
            });

            return convertToDto(saved);

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
    // CREATE BULK (JSON only)
    // ============================================================

    public List<NewsDto> addNewsBulk(List<NewsDto> list) {
        if (list == null || list.isEmpty()) return List.of();

        for (NewsDto dto : list) {
            validate(dto, false, null);
            if (isBlank(dto.getCoverUrl())) {
                throw new BadRequestException("news.coverUrl.required", Map.of("field", "coverUrl"));
            }
        }

        List<News> saved = transactionTemplate.execute(status -> {
            List<News> entities = new ArrayList<>(list.size());

            for (NewsDto dto : list) {
                NewsCategory category = getOrCreateCategory(dto.getCategory());
                NewsSubCategory subCategory = getOrCreateSubCategory(dto.getSubCategory(), category);

                News news = News.builder()
                        .coverUrl(dto.getCoverUrl().trim())
                        .datePublished(dto.getDatePublished())
                        .category(category)
                        .subCategory(subCategory)
                        .contentLanguages(new LinkedHashSet<>(safeLangs(dto.getContentLanguages())))
                        .tagsCkb(new LinkedHashSet<>(safeSet(dto.getTags() != null ? dto.getTags().getCkb() : null)))
                        .tagsKmr(new LinkedHashSet<>(safeSet(dto.getTags() != null ? dto.getTags().getKmr() : null)))
                        .keywordsCkb(new LinkedHashSet<>(safeSet(dto.getKeywords() != null ? dto.getKeywords().getCkb() : null)))
                        .keywordsKmr(new LinkedHashSet<>(safeSet(dto.getKeywords() != null ? dto.getKeywords().getKmr() : null)))
                        .build();

                applyContentByLanguages(news, dto);

                // ✅ upgraded
                attachMediaFromDto(news, dto.getMedia());

                entities.add(news);
            }

            List<News> out = newsRepository.saveAll(entities);

            newsAuditLogRepository.saveAll(
                    out.stream().map(n -> buildAuditLog(n, "CREATE", "news.created.bulk")).toList()
            );

            return out;
        });

        return saved.stream().map(this::convertToDto).toList();
    }

    // ============================================================
    // GET ALL
    // ============================================================

    public List<NewsDto> getAllNews() {
        return transactionTemplate.execute(status -> {
            List<News> newsList = newsRepository.findAllOrderedByDate();
            return newsList.stream().map(n -> {
                n.getMedia().size(); // init lazy
                return convertToDto(n);
            }).toList();
        });
    }

    // ============================================================
    // SEARCH
    // ============================================================

    public List<NewsDto> searchByKeyword(String keyword, String language) {
        if (isBlank(keyword)) return List.of();

        String kw = keyword.trim();
        List<News> results;

        if ("ckb".equalsIgnoreCase(language)) {
            results = newsRepository.searchByKeywordCkb(kw);
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = newsRepository.searchByKeywordKmr(kw);
        } else {
            Set<News> set = new LinkedHashSet<>();
            set.addAll(newsRepository.searchByKeywordCkb(kw));
            set.addAll(newsRepository.searchByKeywordKmr(kw));
            results = new ArrayList<>(set);
        }

        return transactionTemplate.execute(status ->
                results.stream().map(n -> {
                    n.getMedia().size();
                    return convertToDto(n);
                }).toList()
        );
    }

    public List<NewsDto> searchByTag(String tag, String language) {
        if (isBlank(tag)) return List.of();

        String t = tag.trim();
        List<News> results;

        if ("ckb".equalsIgnoreCase(language)) {
            results = newsRepository.findByTagCkb(t);
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = newsRepository.findByTagKmr(t);
        } else {
            Set<News> set = new LinkedHashSet<>();
            set.addAll(newsRepository.findByTagCkb(t));
            set.addAll(newsRepository.findByTagKmr(t));
            results = new ArrayList<>(set);
        }

        return transactionTemplate.execute(status ->
                results.stream().map(n -> {
                    n.getMedia().size();
                    return convertToDto(n);
                }).toList()
        );
    }

    public List<NewsDto> searchByTags(Set<String> tags, String language) {
        if (tags == null || tags.isEmpty()) return List.of();

        List<String> searchTags = tags.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList();
        if (searchTags.isEmpty()) return List.of();

        List<News> results;

        if ("ckb".equalsIgnoreCase(language)) {
            results = newsRepository.findByTagsCkb(searchTags);
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = newsRepository.findByTagsKmr(searchTags);
        } else {
            Set<News> set = new LinkedHashSet<>();
            set.addAll(newsRepository.findByTagsCkb(searchTags));
            set.addAll(newsRepository.findByTagsKmr(searchTags));
            results = new ArrayList<>(set);
        }

        return transactionTemplate.execute(status ->
                results.stream().map(n -> {
                    n.getMedia().size();
                    return convertToDto(n);
                }).toList()
        );
    }

    // ============================================================
    // UPDATE (multipart: optional cover + optional mediaFiles)
    // ============================================================

    public NewsDto updateNews(Long newsId, NewsDto dto, MultipartFile newCoverImage, List<MultipartFile> newMediaFiles) {
        String traceId = MDC.get("traceId");
        log.info("Updating news with files | id={} | traceId={}", newsId, traceId);

        if (newsId == null) throw new BadRequestException("error.validation", Map.of("field", "id"));
        validate(dto, false, null);

        int mediaCount = newMediaFiles != null ? newMediaFiles.size() : 0;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, Math.max(2, 1 + mediaCount)));

        try {
            CompletableFuture<String> coverFuture = CompletableFuture.completedFuture(null);
            if (newCoverImage != null && !newCoverImage.isEmpty()) {
                coverFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return s3Service.upload(coverBytes(newCoverImage), newCoverImage.getOriginalFilename(), newCoverImage.getContentType());
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, pool);
            }

            List<CompletableFuture<UploadedMedia>> mediaFutures = new ArrayList<>();
            if (newMediaFiles != null && !newMediaFiles.isEmpty()) {
                int sort = 0;
                for (MultipartFile f : newMediaFiles) {
                    if (f == null || f.isEmpty()) continue;
                    final int so = sort++;
                    mediaFutures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            String url = s3Service.upload(f.getBytes(), f.getOriginalFilename(), f.getContentType());
                            return new UploadedMedia(detectMediaType(f.getContentType()), url, so);
                        } catch (IOException e) {
                            throw new CompletionException(e);
                        }
                    }, pool));
                }
            }

            String uploadedCoverUrl = coverFuture.join();
            List<UploadedMedia> uploadedMedia = new ArrayList<>();
            for (CompletableFuture<UploadedMedia> f : mediaFutures) uploadedMedia.add(f.join());

            News updated = transactionTemplate.execute(status -> {
                News news = newsRepository.findById(newsId)
                        .orElseThrow(() -> new BadRequestException("news.not_found", Map.of("id", newsId)));

                if (!isBlank(uploadedCoverUrl)) {
                    news.setCoverUrl(uploadedCoverUrl);
                } else if (!isBlank(dto.getCoverUrl())) {
                    news.setCoverUrl(dto.getCoverUrl().trim());
                }

                if (dto.getDatePublished() != null) {
                    news.setDatePublished(dto.getDatePublished());
                }

                if (dto.getCategory() != null) {
                    NewsCategory category = getOrCreateCategory(dto.getCategory());
                    news.setCategory(category);
                }
                if (dto.getSubCategory() != null) {
                    NewsSubCategory subCategory = getOrCreateSubCategory(dto.getSubCategory(), news.getCategory());
                    news.setSubCategory(subCategory);
                }

                news.setContentLanguages(new LinkedHashSet<>(safeLangs(dto.getContentLanguages())));
                applyContentByLanguages(news, dto);

                replaceBilingualSets(news, dto);

                if (!uploadedMedia.isEmpty()) {
                    news.getMedia().clear();
                    for (UploadedMedia um : uploadedMedia) {
                        news.getMedia().add(NewsMedia.builder()
                                .news(news)
                                .type(um.type())
                                .url(um.url())
                                .externalUrl(null)
                                .embedUrl(null)
                                .sortOrder(um.sortOrder())
                                .build());
                    }
                }

                // ✅ upgraded (append/ignore duplicates)
                attachMediaFromDto(news, dto.getMedia());

                News saved = newsRepository.save(news);
                createAuditLog(saved, "UPDATE", "news.updated");
                return saved;
            });

            updated.getMedia().size();
            return convertToDto(updated);

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
    // DELETE
    // ============================================================

    public void deleteNews(Long newsId) {
        if (newsId == null) throw new BadRequestException("error.validation", Map.of("field", "id"));

        transactionTemplate.executeWithoutResult(status -> {
            News news = newsRepository.findById(newsId)
                    .orElseThrow(() -> new BadRequestException("news.not_found", Map.of("id", newsId)));

            createAuditLog(news, "DELETE", "news.deleted");
            newsRepository.delete(news);
        });
    }

    public void deleteNewsBulk(List<Long> newsIds) {
        if (newsIds == null || newsIds.isEmpty()) return;

        transactionTemplate.executeWithoutResult(status -> {
            List<News> list = newsRepository.findAllById(newsIds);
            if (list.isEmpty()) return;

            newsAuditLogRepository.saveAll(
                    list.stream().map(n -> buildAuditLog(n, "DELETE", "news.deleted.bulk")).toList()
            );

            newsRepository.deleteAll(list);
        });
    }

    // ============================================================
    // VALIDATION (Project style)
    // ============================================================

    private void validate(NewsDto dto, boolean coverRequired, MultipartFile coverImage) {
        if (dto == null) throw new BadRequestException("error.validation", Map.of("field", "body"));

        Set<Language> langs = safeLangs(dto.getContentLanguages());
        if (langs.isEmpty()) {
            throw new BadRequestException("news.languages.required", Map.of("field", "contentLanguages"));
        }

        if (coverRequired && (coverImage == null || coverImage.isEmpty())) {
            throw new BadRequestException("news.cover.required", Map.of("field", "cover"));
        }

        if (dto.getCategory() == null || isBlank(dto.getCategory().getCkbName()) || isBlank(dto.getCategory().getKmrName())) {
            throw new BadRequestException("news.category.required", Map.of("field", "category"));
        }
        if (dto.getSubCategory() == null || isBlank(dto.getSubCategory().getCkbName()) || isBlank(dto.getSubCategory().getKmrName())) {
            throw new BadRequestException("news.subcategory.required", Map.of("field", "subCategory"));
        }

        if (langs.contains(Language.CKB)) {
            if (dto.getCkbContent() == null || isBlank(dto.getCkbContent().getTitle())) {
                throw new BadRequestException("news.ckb.title.required", Map.of("field", "ckbContent.title"));
            }
        }
        if (langs.contains(Language.KMR)) {
            if (dto.getKmrContent() == null || isBlank(dto.getKmrContent().getTitle())) {
                throw new BadRequestException("news.kmr.title.required", Map.of("field", "kmrContent.title"));
            }
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private NewsCategory getOrCreateCategory(NewsDto.CategoryDto categoryDto) {
        String ckb = trimOrNull(categoryDto != null ? categoryDto.getCkbName() : null);
        String kmr = trimOrNull(categoryDto != null ? categoryDto.getKmrName() : null);

        if (isBlank(ckb) || isBlank(kmr)) {
            throw new BadRequestException("news.category.required", Map.of("field", "category"));
        }

        return newsCategoryRepository.findByNameCkb(ckb)
                .orElseGet(() -> newsCategoryRepository.save(
                        NewsCategory.builder().nameCkb(ckb).nameKmr(kmr).build()
                ));
    }

    private NewsSubCategory getOrCreateSubCategory(NewsDto.SubCategoryDto subCategoryDto, NewsCategory category) {
        String ckb = trimOrNull(subCategoryDto != null ? subCategoryDto.getCkbName() : null);
        String kmr = trimOrNull(subCategoryDto != null ? subCategoryDto.getKmrName() : null);

        if (isBlank(ckb) || isBlank(kmr)) {
            throw new BadRequestException("news.subcategory.required", Map.of("field", "subCategory"));
        }

        return newsSubCategoryRepository.findByCategoryAndNameCkb(category, ckb)
                .orElseGet(() -> newsSubCategoryRepository.save(
                        NewsSubCategory.builder().nameCkb(ckb).nameKmr(kmr).category(category).build()
                ));
    }

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
    // ✅ MEDIA FROM DTO (UPGRADED: url OR externalUrl OR embedUrl)
    // ============================================================
    private void attachMediaFromDto(News news, List<NewsDto.MediaDto> mediaDtos) {
        if (mediaDtos == null || mediaDtos.isEmpty()) return;

        // existing keys: type + bestLink(url|embed|external)
        Set<String> existing = new HashSet<>();
        for (NewsMedia m : news.getMedia()) {
            String key = mediaKey(m.getType(), m.getUrl(), m.getEmbedUrl(), m.getExternalUrl());
            if (key != null) existing.add(key);
        }

        for (NewsDto.MediaDto m : mediaDtos) {
            if (m == null || m.getType() == null) continue;

            String url = trimOrNull(m.getUrl());
            String externalUrl = trimOrNull(m.getExternalUrl());
            String embedUrl = trimOrNull(m.getEmbedUrl());

            boolean hasUrl = !isBlank(url);
            boolean hasExternal = !isBlank(externalUrl);
            boolean hasEmbed = !isBlank(embedUrl);

            // AUDIO/VIDEO: must have at least one of url/external/embed
            if (m.getType() == NewsMediaType.AUDIO || m.getType() == NewsMediaType.VIDEO) {
                if (!hasUrl && !hasExternal && !hasEmbed) {
                    throw new BadRequestException(
                            "news.media.audio_video_requires_url_or_link",
                            Map.of("type", m.getType().name())
                    );
                }
            } else {
                // other types must have direct url
                if (!hasUrl) {
                    throw new BadRequestException(
                            "news.media.url_required",
                            Map.of("type", m.getType().name())
                    );
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

    private String mediaKey(NewsMediaType type, String url, String embedUrl, String externalUrl) {
        if (type == null) return null;
        String best = trimOrNull(url);
        if (best == null) best = trimOrNull(embedUrl);
        if (best == null) best = trimOrNull(externalUrl);
        if (best == null) return null;
        return type.name() + "|" + best;
    }

    private NewsMediaType detectMediaType(String contentType) {
        if (contentType == null) return NewsMediaType.DOCUMENT;

        String type = contentType.toLowerCase();
        if (type.startsWith("image/")) return NewsMediaType.IMAGE;
        if (type.startsWith("video/")) return NewsMediaType.VIDEO;
        if (type.startsWith("audio/")) return NewsMediaType.AUDIO;
        return NewsMediaType.DOCUMENT;
    }

    private void createAuditLog(News news, String action, String messageKey) {
        newsAuditLogRepository.save(buildAuditLog(news, action, messageKey));
    }

    private NewsAuditLog buildAuditLog(News news, String action, String noteKey) {
        return NewsAuditLog.builder()
                .news(news)
                .action(action)
                .performedBy("system")
                .note(noteKey)
                .build();
    }

    // ============================================================
    // ✅ DTO MAPPING (UPGRADED: externalUrl/embedUrl)
    // ============================================================
    private NewsDto convertToDto(News news) {
        NewsDto dto = NewsDto.builder()
                .id(news.getId())
                .coverUrl(news.getCoverUrl())
                .datePublished(news.getDatePublished())
                .createdAt(news.getCreatedAt())
                .updatedAt(news.getUpdatedAt())
                .contentLanguages(news.getContentLanguages() != null ? new LinkedHashSet<>(news.getContentLanguages()) : new LinkedHashSet<>())
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

        dto.setTags(NewsDto.BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(news.getTagsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(news.getTagsKmr())))
                .build());

        dto.setKeywords(NewsDto.BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(news.getKeywordsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(news.getKeywordsKmr())))
                .build());

        if (news.getMedia() != null && !news.getMedia().isEmpty()) {
            List<NewsDto.MediaDto> mediaDtos = news.getMedia().stream()
                    .sorted(Comparator.comparingInt(m -> m.getSortOrder() != null ? m.getSortOrder() : 0))
                    .map(media -> NewsDto.MediaDto.builder()
                            .id(media.getId())
                            .type(media.getType())
                            .url(media.getUrl())
                            .externalUrl(media.getExternalUrl())
                            .embedUrl(media.getEmbedUrl())
                            .sortOrder(media.getSortOrder())
                            .createdAt(media.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());

            dto.setMedia(mediaDtos);
        } else {
            dto.setMedia(List.of());
        }

        return dto;
    }

    // ============================================================
    // small utils
    // ============================================================

    private byte[] coverBytes(MultipartFile f) throws IOException {
        if (f == null || f.isEmpty()) throw new BadRequestException("news.cover.required", Map.of("field", "cover"));
        return f.getBytes();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Set<Language> safeLangs(Set<Language> langs) {
        return langs == null ? Set.of() : langs;
    }

    private <T> Set<T> safeSet(Set<T> s) {
        return s == null ? Set.of() : s;
    }

    private Set<String> cleanStrings(Set<String> input) {
        if (input == null || input.isEmpty()) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : input) {
            if (s != null && !s.isBlank()) out.add(s.trim());
        }
        return out;
    }

    private record UploadedMedia(NewsMediaType type, String url, int sortOrder) {}
}
