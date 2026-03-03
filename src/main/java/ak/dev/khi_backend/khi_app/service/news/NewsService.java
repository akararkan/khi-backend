package ak.dev.khi_backend.khi_app.service.news;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * سێرڤیسی هەواڵ - بەڕێوەبردنی هەموو کردارەکانی هەواڵەکان
 *
 * لیستی هەڵە کوردیەکان کە بەکاردێن:
 *
 * ١. "error.validation" = "هەڵەی پشتڕاستکردنەوە: زانیاری نادروستە یان پێویستە" (Bad Request)
 * ٢. "news.cover.required" = "وێنەی بەرگی هەواڵ پێویستە: دەبێت وێنەیەک دابنرێت" (Bad Request)
 * ٣. "news.coverUrl.required" = "لینکی وێنەی بەرگ پێویستە: لە دۆخی بەکڵک دەبێت لینک دابنرێت" (Bad Request)
 * ٤. "news.not_found" = "هەواڵەکە نەدۆزرایەوە: ئایدییەکە بوونی نییە لە سیستەم" (Bad Request/Not Found)
 * ٥. "news.languages.required" = "زمانی هەواڵ پێویستە: دەبێت لانیکەم یەک زمان هەڵبژێردرێت" (Bad Request)
 * ٦. "news.category.required" = "هاوپۆلی هەواڵ پێویستە: دەبێت هاوپۆل دیاری بکرێت" (Bad Request)
 * ٧. "news.subcategory.required" = "هاوپۆلی فرعی پێویستە: دەبێت هاوپۆلی خوارەوە دیاری بکرێت" (Bad Request)
 * ٨. "news.ckb.title.required" = "ناونیشانی کوردیی ناوەندی پێویستە: دەبێت ناونیشان بە سۆرانی بنووسرێت" (Bad Request)
 * ٩. "news.kmr.title.required" = "ناونیشانی کوردیی باکووری پێویستە: دەبێت ناونیشان بە کورمانجی بنووسرێت" (Bad Request)
 * ١٠. "media.upload.failed" = "شکستی ناردنی فایل: کێشە لە ناردنی فایل بۆ سێرڤەری ستوorage" (Bad Request)
 * ١١. "news.media.audio_video_requires_url_or_link" = "ئۆدیۆ/ڤیدیۆ پێویستی بە لینکە: دەبێت لینکی ڕاستەقینە یان دەرەکی یان ئێمبێد هەبێت" (Bad Request)
 * ١٢. "news.media.url_required" = "لینکی میدیا پێویستە: بۆ ئەم جۆرە لە میدیا دەبێت لینک دابمەزرێت" (Bad Request)
 */
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
    // دروستکردن (لەگەڵ فایل: وێنەی بەرگ + فایلی میدیا)
    //
    // ✅ وێنەی بەرگ:
    //   - ئەگەر فایل هەبێت -> ناردن بۆ S3 و بەکارهێنان
    //   - ئەگەر نەبێت -> دەبێت لینک لە dto.coverUrl دا بێت
    //
    // ✅ میدیا:
    //   - ئەگەر فایلی میدیا هەبێت -> ناردن و بەکارهێنان (dto.media بەتاڵ بێت شایەنیە)
    //   - ئەگەر نەبێت -> بەکارهێنانی لینکی dto.media
    // ============================================================

    /**
     * زیادکردنی هەواڵی نوێ لەگەڵ فایلەکان
     *
     * @throws BadRequestException    - ئەگەر وێنەی بەرگ نەبێت ("وێنەی بەرگی هەواڵ پێویستە")
     * @throws BadRequestException    - کێشە لە ناردنی فایل ("شکستی ناردنی فایل")
     * @throws BadRequestException    - زمان نەدیاری بکرێت ("زمانی هەواڵ پێویستە")
     * @throws BadRequestException    - هاوپۆل نەدیاری بکرێت ("هاوپۆلی هەواڵ پێویستە")
     * @throws BadRequestException    - ناونیشان بە کوردی نەنووسرێت ("ناونیشانی کوردیی ناوەندی پێویستە")
     */
    public NewsDto addNews(NewsDto dto, MultipartFile coverImage, List<MultipartFile> mediaFiles) {
        String traceId = traceId();
        log.info("دروستکردنی هەواڵ | traceId={}", traceId);

        boolean hasCoverFile = hasFile(coverImage);
        boolean hasMediaFiles = hasFiles(mediaFiles);

        // وێنەی بەرگ پێویستە: فایل یان لینک
        validate(dto, true, hasCoverFile);

        int mediaCount = mediaFiles != null ? mediaFiles.size() : 0;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, Math.max(2, 1 + mediaCount)));

        try {
            // ---------- ناردنی فایلەکان پێش ترانزاکشن ----------
            CompletableFuture<String> coverFuture = CompletableFuture.completedFuture(trimOrNull(dto.getCoverUrl()));
            if (hasCoverFile) {
                coverFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return s3Service.upload(coverBytes(coverImage), coverImage.getOriginalFilename(), coverImage.getContentType());
                    } catch (IOException e) {
                        // هەڵە: کێشە لە خوێندنەوە یان ناردنی وێنە
                        throw new CompletionException("کێشە لە ناردنی وێنەی بەرگ: " + e.getMessage(), e);
                    }
                }, pool);
            }

            List<CompletableFuture<UploadedMedia>> mediaFutures = new ArrayList<>();
            if (hasMediaFiles) {
                int sort = 0;
                for (MultipartFile f : mediaFiles) {
                    if (!hasFile(f)) continue;
                    final int so = sort++;
                    mediaFutures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            String url = s3Service.upload(f.getBytes(), f.getOriginalFilename(), f.getContentType());
                            return new UploadedMedia(detectMediaType(f.getContentType()), url, so);
                        } catch (IOException e) {
                            // هەڵە: کێشە لە ناردنی فایلی میدیا
                            throw new CompletionException("کێشە لە ناردنی فایلی میدیا: " + f.getOriginalFilename(), e);
                        }
                    }, pool));
                }
            }

            String coverUrl = trimOrNull(coverFuture.join());
            if (isBlank(coverUrl)) {
                // هەڵە: وێنەی بەرگ نەنێردراوە نە فایل نە لینک
                throw new BadRequestException("news.cover.required", Map.of("field", "coverImage_or_coverUrl", "traceId", traceId));
            }

            List<UploadedMedia> uploadedMedia = new ArrayList<>();
            for (CompletableFuture<UploadedMedia> f : mediaFutures) uploadedMedia.add(f.join());

            // ---------- ترانزاکشنی بنکەدراو ----------
            News saved = transactionTemplate.execute(status -> {
                NewsCategory category = getOrCreateCategory(dto.getCategory());
                NewsSubCategory subCategory = getOrCreateSubCategory(dto.getSubCategory(), category);

                News news = News.builder()
                        .coverUrl(coverUrl)
                        .datePublished(dto.getDatePublished())
                        .category(category)
                        .subCategory(subCategory)
                        .contentLanguages(new LinkedHashSet<>(safeLangs(dto.getContentLanguages())))
                        .tagsCkb(new LinkedHashSet<>(cleanStrings(dto.getTags() != null ? dto.getTags().getCkb() : null)))
                        .tagsKmr(new LinkedHashSet<>(cleanStrings(dto.getTags() != null ? dto.getTags().getKmr() : null)))
                        .keywordsCkb(new LinkedHashSet<>(cleanStrings(dto.getKeywords() != null ? dto.getKeywords().getCkb() : null)))
                        .keywordsKmr(new LinkedHashSet<>(cleanStrings(dto.getKeywords() != null ? dto.getKeywords().getKmr() : null)))
                        .build();

                applyContentByLanguages(news, dto);

                // میدیا لە فایلە نێردراوەکان
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
                } else {
                    // ✅ تەنها ئەگەر فایل نەبێت -> بەکارهێنانی لینکی dto.media
                    attachMediaFromDto(news, dto.getMedia());
                }

                News p = newsRepository.save(news);
                createAuditLog(p, "CREATE", "هەواڵ دروستکرا");
                return p;
            });

            saved.getMedia().size();
            return convertToDto(saved);

        } catch (CompletionException ex) {
            Throwable root = ex.getCause() != null ? ex.getCause() : ex;
            if (root instanceof IOException) {
                // هەڵە: شکستی ناردنی فایل بۆ S3
                throw new BadRequestException("media.upload.failed", Map.of("traceId", traceId));
            }
            throw ex;
        } finally {
            pool.shutdown();
        }
    }

    // ============================================================
    // دروستکردنی بە کۆمەڵ (تەنها JSON)
    //
    // ✅ میدیا ئارەزوومەندانەیە
    // ✅ لینکی وێنەی بەرگ پێویستە بۆ هەر دانەیەک (بێ فایل لە بەکڵک)
    // ============================================================

    /**
     * زیادکردنی کۆمەڵە هەواڵێک بە یەکجار (JSON)
     *
     * @throws BadRequestException - ئەگەر لیست بەتاڵ بێت
     * @throws BadRequestException - لینکی وێنە نەبێت ("لینکی وێنەی بەرگ پێویستە")
     * @throws BadRequestException - هەر هەڵەیەکی پشتڕاستکردنەوە
     */
    public List<NewsDto> addNewsBulk(List<NewsDto> list) {
        if (list == null || list.isEmpty()) {
            // هەڵە: لیستی هەواڵەکان بەتاڵە
            throw new BadRequestException("error.validation", Map.of("field", "list", "message", "لیستی هەواڵەکان بەتاڵە"));
        }

        for (NewsDto dto : list) {
            validate(dto, false, false);
            if (isBlank(dto.getCoverUrl())) {
                // هەڵە: لە دۆخی بەکڵک دەبێت لینکی وێنە بنێرێت
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
                        .tagsCkb(new LinkedHashSet<>(cleanStrings(dto.getTags() != null ? dto.getTags().getCkb() : null)))
                        .tagsKmr(new LinkedHashSet<>(cleanStrings(dto.getTags() != null ? dto.getTags().getKmr() : null)))
                        .keywordsCkb(new LinkedHashSet<>(cleanStrings(dto.getKeywords() != null ? dto.getKeywords().getCkb() : null)))
                        .keywordsKmr(new LinkedHashSet<>(cleanStrings(dto.getKeywords() != null ? dto.getKeywords().getKmr() : null)))
                        .build();

                applyContentByLanguages(news, dto);

                // ✅ بەکڵک بەکاردەهێنێت dto.media (ئارەزوومەندانە)
                attachMediaFromDto(news, dto.getMedia());

                entities.add(news);
            }

            List<News> out = newsRepository.saveAll(entities);

            newsAuditLogRepository.saveAll(
                    out.stream().map(n -> buildAuditLog(n, "CREATE", "هەواڵ بە کۆمەڵ دروستکرا")).toList()
            );

            return out;
        });

        return saved.stream().map(this::convertToDto).toList();
    }

    // ============================================================
    // هێنانی هەموو هەواڵەکان
    // ============================================================
    public List<NewsDto> getAllNews() {
        return transactionTemplate.execute(status -> {
            List<News> newsList = newsRepository.findAllOrderedByDate();
            return newsList.stream().map(n -> {
                n.getMedia().size();
                return convertToDto(n);
            }).toList();
        });
    }

    // ============================================================
    // گەڕان بەپێی کلیلەووشە
    // ============================================================

    /**
     * گەڕانی هەواڵ بەپێی کلیلەووشە
     *
     * @throws BadRequestException - کێشە لە پارامیتەرەکان (ئەگەر کلیلەووشە بەتاڵ بێت)
     */
    public List<NewsDto> searchByKeyword(String keyword, String language) {
        if (isBlank(keyword)) {
            // هەڵە: کلیلەووشەی گەڕان بەتاڵە
            throw new BadRequestException("keyword.required", Map.of("message", "کلیلەووشەی گەڕان بەتاڵە"));
        }

        String kw = keyword.trim();

        return transactionTemplate.execute(status -> {
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

            return results.stream().map(n -> {
                // initialize lazy collections needed by DTO
                n.getMedia().size();
                n.getTagsCkb().size();
                n.getTagsKmr().size();
                n.getKeywordsCkb().size();
                n.getKeywordsKmr().size();
                n.getContentLanguages().size();
                return convertToDto(n);
            }).toList();
        });
    }

    /**
     * گەڕانی هەواڵ بەپێی تاگ
     *
     * @throws BadRequestException - ئەگەر تاگ بەتاڵ بێت ("تاگی پێویستە")
     */
    public List<NewsDto> searchByTag(String tag, String language) {
        if (isBlank(tag)) {
            // هەڵە: تاگی گەڕان بەتاڵە
            throw new BadRequestException("tag.required", Map.of("message", "تاگی گەڕان پێویستە"));
        }

        String t = tag.trim();

        return transactionTemplate.execute(status -> {
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

            return results.stream().map(n -> {
                n.getMedia().size();
                n.getTagsCkb().size();
                n.getTagsKmr().size();
                n.getKeywordsCkb().size();
                n.getKeywordsKmr().size();
                n.getContentLanguages().size();
                return convertToDto(n);
            }).toList();
        });
    }

    /**
     * گەڕانی هەواڵ بەپێی کۆمەڵێک تاگ
     */
    public List<NewsDto> searchByTags(Set<String> tags, String language) {
        if (tags == null || tags.isEmpty()) {
            // هەڵە: کۆمەڵەی تاگەکان بەتاڵە
            throw new BadRequestException("tag.required", Map.of("message", "لانیکەم یەک تاگ پێویستە"));
        }

        List<String> searchTags = tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        if (searchTags.isEmpty()) {
            throw new BadRequestException("tag.required", Map.of("message", "تاگەکان نادروستن"));
        }

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
    // نوێکردنەوە (لەگەڵ فایل)
    //
    // ✅ لۆژیکی میدیا:
    //   - ئەگەر فایلی میدیای نوێ هەبێت -> بیگۆڕە بە فایلە نێردراوەکان (dto.media پشتگوێ بخە)
    //   - ئەگەر نەبێت و dto.media ≠ null -> بیگۆڕە بە dto.media (لیستی بەتاڵ مانای سڕینەوەیە)
    //   - ئەگەر هیچ نەبێت -> هیچ میدیایەک مەگۆڕە
    // ============================================================

    /**
     * نوێکردنەوەی هەواڵ لەگەڵ فایلی نوێ
     *
     * @throws BadRequestException    - ئایدی بەتاڵە ("هەڵەی پشتڕاستکردنەوە")
     * @throws BadRequestException    - هەواڵەکە نەدۆزرایەوە ("هەواڵەکە نەدۆزرایەوە")
     * @throws BadRequestException    - وێنەی بەرگ نەبێت ("وێنەی بەرگی هەواڵ پێویستە")
     * @throws BadRequestException    - شکستی ناردنی فایل ("شکستی ناردنی فایل")
     */
    public NewsDto updateNews(Long newsId, NewsDto dto, MultipartFile newCoverImage, List<MultipartFile> newMediaFiles) {
        String traceId = traceId();
        log.info("نوێکردنەوەی هەواڵ | id={} | traceId={}", newsId, traceId);

        if (newsId == null) {
            // هەڵە: ئایدیی هەواڵ پێویستە
            throw new BadRequestException("error.validation", Map.of("field", "id", "message", "ئایدیی هەواڵ پێویستە"));
        }

        boolean hasNewCoverFile = hasFile(newCoverImage);
        boolean hasNewMediaFiles = hasFiles(newMediaFiles);

        validate(dto, false, false);

        int mediaCount = newMediaFiles != null ? newMediaFiles.size() : 0;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, Math.max(2, 1 + mediaCount)));

        try {
            CompletableFuture<String> coverFuture = CompletableFuture.completedFuture(null);
            if (hasNewCoverFile) {
                coverFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return s3Service.upload(coverBytes(newCoverImage), newCoverImage.getOriginalFilename(), newCoverImage.getContentType());
                    } catch (IOException e) {
                        throw new CompletionException("کێشە لە ناردنی وێنەی بەرگی نوێ", e);
                    }
                }, pool);
            }

            List<CompletableFuture<UploadedMedia>> mediaFutures = new ArrayList<>();
            if (hasNewMediaFiles) {
                int sort = 0;
                for (MultipartFile f : newMediaFiles) {
                    if (!hasFile(f)) continue;
                    final int so = sort++;
                    mediaFutures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            String url = s3Service.upload(f.getBytes(), f.getOriginalFilename(), f.getContentType());
                            return new UploadedMedia(detectMediaType(f.getContentType()), url, so);
                        } catch (IOException e) {
                            throw new CompletionException("کێشە لە ناردنی میدیا", e);
                        }
                    }, pool));
                }
            }

            String uploadedCoverUrl = trimOrNull(coverFuture.join());

            List<UploadedMedia> uploadedMedia = new ArrayList<>();
            for (CompletableFuture<UploadedMedia> f : mediaFutures) uploadedMedia.add(f.join());

            News updated = transactionTemplate.execute(status -> {
                News news = newsRepository.findById(newsId)
                        .orElseThrow(() -> {
                            // هەڵە: هەواڵەکە نەدۆزرایەوە لە بنکەدراو
                            return new BadRequestException("news.not_found", Map.of("id", newsId));
                        });

                // ✅ لۆژیکی وێنە (ڕێگە مەدە بە ئەنجامی بەتاڵ)
                if (!isBlank(uploadedCoverUrl)) {
                    news.setCoverUrl(uploadedCoverUrl);
                } else if (!isBlank(dto.getCoverUrl())) {
                    news.setCoverUrl(dto.getCoverUrl().trim());
                } else {
                    // هێشتنەوەی وێنەی کۆن؛ بەڵام ئەگەر ئەوەش بەتاڵ بێت -> هەڵە
                    if (isBlank(news.getCoverUrl())) {
                        throw new BadRequestException("news.cover.required", Map.of("field", "coverImage_or_coverUrl"));
                    }
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

                // ✅ لۆژیکی میدیا
                if (!uploadedMedia.isEmpty()) {
                    // گۆڕین بە فایلە نێردراوەکان
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
                } else if (dto.getMedia() != null) {
                    // گۆڕین بە dto media (لیستی بەتاڵ مانای سڕینەوە)
                    news.getMedia().clear();
                    attachMediaFromDto(news, dto.getMedia());
                } else {
                    // هیچ مەگۆڕە
                }

                News saved = newsRepository.save(news);
                createAuditLog(saved, "UPDATE", "هەواڵ نوێکرایەوە");
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
    // سڕینەوە
    // ============================================================

    /**
     * سڕینەوەی هەواڵێک
     *
     * @throws BadRequestException - ئایدی بەتاڵە
     * @throws BadRequestException - هەواڵەکە نەدۆزرایەوە ("هەواڵەکە نەدۆزرایەوە")
     */
    public void deleteNews(Long newsId) {
        if (newsId == null) {
            // هەڵە: ئایدی بۆ سڕینەوە پێویستە
            throw new BadRequestException("error.validation", Map.of("field", "id", "message", "ئایدیی هەواڵ بۆ سڕینەوە پێویستە"));
        }

        transactionTemplate.executeWithoutResult(status -> {
            News news = newsRepository.findById(newsId)
                    .orElseThrow(() -> {
                        // هەڵە: هەواڵەکە نەدۆزرایەوە بۆ سڕینەوە
                        return new BadRequestException("news.not_found", Map.of("id", newsId));
                    });

            createAuditLog(news, "DELETE", "هەواڵ سڕایەوە");
            newsRepository.delete(news);
        });
    }

    /**
     * سڕینەوەی کۆمەڵەیەک هەواڵ
     *
     * @throws BadRequestException - لیستی ئایدیەکان بەتاڵە
     */
    public void deleteNewsBulk(List<Long> newsIds) {
        if (newsIds == null || newsIds.isEmpty()) {
            // هەڵە: لیستی ئایدیەکان بۆ سڕینەوە بەتاڵە
            throw new BadRequestException("error.validation", Map.of("field", "newsIds", "message", "لیستی ئایدیەکان بەتاڵە"));
        }

        transactionTemplate.executeWithoutResult(status -> {
            List<News> list = newsRepository.findAllById(newsIds);
            if (list.isEmpty()) {
                // هەڵە: هیچ هەواڵێک نەدۆزرایەوە بۆ ئەم ئایدیانە
                throw new BadRequestException("news.not_found", Map.of("ids", newsIds));
            }

            newsAuditLogRepository.saveAll(
                    list.stream().map(n -> buildAuditLog(n, "DELETE", "هەواڵ بە کۆمەڵ سڕایەوە")).toList()
            );

            newsRepository.deleteAll(list);
        });
    }

    // ============================================================
    // پشتڕاستکردنەوە
    //
    // createRequiresCover=true -> وێنەی بەرگ پێویستە (فایل یان dto.coverUrl)
    // ============================================================

    /**
     * پشتڕاستکردنەوەی زانیاری هەواڵ
     *
     * هەڵەکان بە کوردی:
     * - "error.validation" = هەڵەی پشتڕاستکردنەوە (DTO بەتاڵە)
     * - "news.languages.required" = زمانی هەواڵ پێویستە
     * - "news.cover.required" = وێنەی بەرگی پێویستە
     * - "news.category.required" = هاوپۆلی هەواڵ پێویستە
     * - "news.subcategory.required" = هاوپۆلی فرعی پێویستە
     * - "news.ckb.title.required" = ناونیشانی کوردیی ناوەندی پێویستە
     * - "news.kmr.title.required" = ناونیشانی کوردیی باکووری پێویستە
     */
    private void validate(NewsDto dto, boolean createRequiresCover, boolean hasCoverFile) {
        if (dto == null) {
            // هەڵە: داواکاری نەنێردراوە یان بەتاڵە
            throw new BadRequestException("error.validation", Map.of("field", "body", "message", "داواکاری پێویستە"));
        }

        Set<Language> langs = safeLangs(dto.getContentLanguages());
        if (langs.isEmpty()) {
            // هەڵە: هیچ زمانێک دیاری نەکراوە
            throw new BadRequestException("news.languages.required", Map.of("field", "contentLanguages"));
        }

        if (createRequiresCover) {
            boolean hasCoverUrl = !isBlank(dto.getCoverUrl());
            if (!hasCoverFile && !hasCoverUrl) {
                // هەڵە: نە فایلە و نە لینکی وێنە
                throw new BadRequestException("news.cover.required", Map.of("field", "coverImage_or_coverUrl"));
            }
        }

        if (dto.getCategory() == null || isBlank(dto.getCategory().getCkbName()) || isBlank(dto.getCategory().getKmrName())) {
            // هەڵە: هاوپۆل نەدیاری کراوە
            throw new BadRequestException("news.category.required", Map.of("field", "category"));
        }
        if (dto.getSubCategory() == null || isBlank(dto.getSubCategory().getCkbName()) || isBlank(dto.getSubCategory().getKmrName())) {
            // هەڵە: هاوپۆلی فرعی نەدیاری کراوە
            throw new BadRequestException("news.subcategory.required", Map.of("field", "subCategory"));
        }

        if (langs.contains(Language.CKB)) {
            if (dto.getCkbContent() == null || isBlank(dto.getCkbContent().getTitle())) {
                // هەڵە: ناونیشان بە کوردیی ناوەندی (سۆرانی) پێویستە
                throw new BadRequestException("news.ckb.title.required", Map.of("field", "ckbContent.title"));
            }
        }
        if (langs.contains(Language.KMR)) {
            if (dto.getKmrContent() == null || isBlank(dto.getKmrContent().getTitle())) {
                // هەڵە: ناونیشان بە کوردیی باکووری (کورمانجی) پێویستە
                throw new BadRequestException("news.kmr.title.required", Map.of("field", "kmrContent.title"));
            }
        }
    }

    // ============================================================
    // هاوپۆل و هاوپۆلی فرعی
    // ============================================================

    /**
     * دۆزینەوە یان دروستکردنی هاوپۆل
     *
     * @throws BadRequestException - ناوی هاوپۆل بەتاڵە ("هاوپۆلی هەواڵ پێویستە")
     */
    private NewsCategory getOrCreateCategory(NewsDto.CategoryDto categoryDto) {
        String ckb = trimOrNull(categoryDto != null ? categoryDto.getCkbName() : null);
        String kmr = trimOrNull(categoryDto != null ? categoryDto.getKmrName() : null);

        if (isBlank(ckb) || isBlank(kmr)) {
            // هەڵە: ناوی هاوپۆل بە کوردی پێویستە
            throw new BadRequestException("news.category.required", Map.of("field", "category"));
        }

        NewsCategory existing = newsCategoryRepository.findByNameCkb(ckb).orElse(null);
        if (existing != null) {
            if (!isBlank(kmr) && !kmr.equals(existing.getNameKmr())) {
                existing.setNameKmr(kmr);
                existing = newsCategoryRepository.save(existing);
            }
            return existing;
        }

        return newsCategoryRepository.save(NewsCategory.builder()
                .nameCkb(ckb)
                .nameKmr(kmr)
                .build());
    }

    /**
     * دۆزینەوە یان دروستکردنی هاوپۆلی فرعی
     *
     * @throws BadRequestException - ناو بەتاڵە ("هاوپۆلی فرعی پێویستە")
     */
    private NewsSubCategory getOrCreateSubCategory(NewsDto.SubCategoryDto subCategoryDto, NewsCategory category) {
        String ckb = trimOrNull(subCategoryDto != null ? subCategoryDto.getCkbName() : null);
        String kmr = trimOrNull(subCategoryDto != null ? subCategoryDto.getKmrName() : null);

        if (isBlank(ckb) || isBlank(kmr)) {
            // هەڵە: ناوی هاوپۆلی فرعی پێویستە
            throw new BadRequestException("news.subcategory.required", Map.of("field", "subCategory"));
        }

        NewsSubCategory existing = newsSubCategoryRepository.findByCategoryAndNameCkb(category, ckb).orElse(null);
        if (existing != null) {
            if (!isBlank(kmr) && !kmr.equals(existing.getNameKmr())) {
                existing.setNameKmr(kmr);
                existing = newsSubCategoryRepository.save(existing);
            }
            return existing;
        }

        return newsSubCategoryRepository.save(NewsSubCategory.builder()
                .nameCkb(ckb)
                .nameKmr(kmr)
                .category(category)
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
    // میدیا لە DTO (ئارەزوومەندانە)
    // ============================================================

    /**
     * لکاندنی میدیا لە DTO بۆ ئینتیتی هەواڵ
     *
     * @throws BadRequestException - "news.media.audio_video_requires_url_or_link" = ئۆدیۆ/ڤیدیۆ پێویستی بە لینکە
     * @throws BadRequestException - "news.media.url_required" = لینکی میدیا پێویستە
     */
    private void attachMediaFromDto(News news, List<NewsDto.MediaDto> mediaDtos) {
        if (mediaDtos == null || mediaDtos.isEmpty()) return;

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

            if (m.getType() == NewsMediaType.AUDIO || m.getType() == NewsMediaType.VIDEO) {
                if (!hasUrl && !hasExternal && !hasEmbed) {
                    // هەڵە: بۆ ئۆدیۆ یان ڤیدیۆ دەبێت لینکی ڕاستەقینە یان دەرەکی یان ئێمبێد هەبێت
                    throw new BadRequestException(
                            "news.media.audio_video_requires_url_or_link",
                            Map.of("type", m.getType().name())
                    );
                }
            } else {
                if (!hasUrl) {
                    // هەڵە: بۆ وێنە و فایلی دیکە دەبێت لینکی ڕاستەقینە هەبێت
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

    // ============================================================
    // تۆمارکردنی چالاکی (Audit)
    // ============================================================
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

    /**
     * هێنانی هەواڵ بەپێی ئایدی
     *
     * @throws NotFoundException - ئەگەر هەواڵەکە نەدۆزرێتەوە ("هەواڵەکە نەدۆزرایەوە")
     */
    @Transactional()
    public NewsDto getNewsById(Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> {
                    // هەڵە: هەواڵەکە نەدۆزرایەوە
                    return new NotFoundException("news.not_found", Map.of("id", id));
                });

        return convertToDto(news);
    }

    // ============================================================
    // گۆڕین بۆ DTO
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
    // یاریدەدەرەکان
    // ============================================================
    private boolean hasFile(MultipartFile f) {
        return f != null && !f.isEmpty();
    }

    private boolean hasFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return false;
        for (MultipartFile f : files) {
            if (hasFile(f)) return true;
        }
        return false;
    }

    private byte[] coverBytes(MultipartFile f) throws IOException {
        if (f == null || f.isEmpty()) {
            // هەڵە: فایلی وێنەی بەرگ بەتاڵە
            throw new BadRequestException("news.cover.required", Map.of("field", "cover"));
        }
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

    private String traceId() {
        String t = MDC.get("traceId");
        return (t != null && !t.isBlank()) ? t : UUID.randomUUID().toString();
    }

    private record UploadedMedia(NewsMediaType type, String url, int sortOrder) {}
}