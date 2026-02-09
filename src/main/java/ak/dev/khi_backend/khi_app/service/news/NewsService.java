package ak.dev.khi_backend.khi_app.service.news;

import ak.dev.khi_backend.khi_app.dto.news.NewsDto;
import ak.dev.khi_backend.khi_app.enums.news.NewsMediaType;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.model.news.*;
import ak.dev.khi_backend.khi_app.repository.news.NewsAuditLogRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsCategoryRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsSubCategoryRepository;
import ak.dev.khi_backend.khi_app.model.news.*;
import ak.dev.khi_backend.khi_app.repository.news.*;
import ak.dev.khi_backend.khi_app.service.S3Service;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
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

    @Transactional
    public NewsDto addNews(NewsDto dto, MultipartFile coverImage, List<MultipartFile> mediaFiles, String performedBy) {
        log.info("📰 Creating single news - Category: {}, SubCategory: {}", dto.getCategoryName(), dto.getSubCategoryName());

        try {
            if (coverImage == null || coverImage.isEmpty()) {
                log.error("❌ Cover image is required");
                throw new BadRequestException("cover.required", "Cover image is required");
            }

            log.debug("📤 Uploading cover image to S3: {}", coverImage.getOriginalFilename());
            String coverUrl = s3Service.upload(
                    coverImage.getBytes(),
                    coverImage.getOriginalFilename(),
                    coverImage.getContentType()
            );
            log.info("✅ Cover image uploaded to S3: {}", coverUrl);

            NewsCategory category = getOrCreateCategory(dto.getCategoryName());
            NewsSubCategory subCategory = getOrCreateSubCategory(dto.getSubCategoryName(), category);

            News news = News.builder()
                    .coverUrl(coverUrl)
                    .datePublished(dto.getDatePublished())
                    .category(category)
                    .subCategory(subCategory)
                    .tags(Optional.ofNullable(dto.getTags()).orElse(new LinkedHashSet<>()))
                    .keywords(Optional.ofNullable(dto.getKeywords()).orElse(new LinkedHashSet<>()))
                    .build();

            setBilingualContent(news, dto);

            if (mediaFiles != null && !mediaFiles.isEmpty()) {
                log.info("📤 Uploading {} media files to S3", mediaFiles.size());
                List<NewsMedia> mediaList = processMediaFiles(mediaFiles, news);
                news.setMedia(mediaList);
                log.info("✅ {} media files uploaded to S3", mediaList.size());
            }

            News savedNews = newsRepository.save(news);
            log.info("✅ News created successfully - ID: {}", savedNews.getId());

            createAuditLog(savedNews, "CREATE", performedBy, "News created with S3 media");

            return convertToDto(savedNews);

        } catch (IOException e) {
            log.error("❌ S3 upload failed", e);
            throw new BadRequestException("media.upload.failed", "Failed to upload files to S3");
        }
    }

    @Transactional
    public List<NewsDto> addNewsBulk(List<NewsDto> newsDtoList, String performedBy) {
        log.info("📰 Starting bulk news creation - Count: {}", newsDtoList.size());

        if (newsDtoList == null || newsDtoList.isEmpty()) {
            log.warn("⚠️ Empty news list provided for bulk creation");
            return Collections.emptyList();
        }

        newsDtoList.forEach(dto -> {
            if (dto.getCoverUrl() == null || dto.getCoverUrl().trim().isEmpty()) {
                log.error("❌ Cover URL is missing for bulk news");
                throw new BadRequestException("cover.url.required", "Cover URL is required for bulk operation");
            }
        });

        List<News> savedNewsList = new ArrayList<>();

        for (NewsDto dto : newsDtoList) {
            NewsCategory category = getOrCreateCategory(dto.getCategoryName());
            NewsSubCategory subCategory = getOrCreateSubCategory(dto.getSubCategoryName(), category);

            News news = News.builder()
                    .coverUrl(dto.getCoverUrl())
                    .datePublished(dto.getDatePublished())
                    .category(category)
                    .subCategory(subCategory)
                    .tags(Optional.ofNullable(dto.getTags()).orElse(new LinkedHashSet<>()))
                    .keywords(Optional.ofNullable(dto.getKeywords()).orElse(new LinkedHashSet<>()))
                    .build();

            setBilingualContent(news, dto);

            if (dto.getMedia() != null && !dto.getMedia().isEmpty()) {
                List<NewsMedia> mediaList = dto.getMedia().stream()
                        .map(mediaDto -> buildNewsMedia(mediaDto, news))
                        .collect(Collectors.toList());
                news.setMedia(mediaList);
            }

            savedNewsList.add(news);
        }

        List<News> saved = newsRepository.saveAll(savedNewsList);
        log.info("✅ Successfully saved {} news items", saved.size());

        List<NewsAuditLog> auditLogs = saved.stream()
                .map(news -> buildAuditLog(news, "CREATE", performedBy, "News created in bulk"))
                .collect(Collectors.toList());

        newsAuditLogRepository.saveAll(auditLogs);

        return saved.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * ✅ GET ALL NEWS - Explicitly initialize media within transaction
     */
    @Transactional(readOnly = true)
    public List<NewsDto> getAllNews() {
        log.info("📋 Fetching all news");
        List<News> newsList = newsRepository.findAllOrderedByDate();
        log.info("✅ Retrieved {} news items", newsList.size());

        // Convert to DTOs within transaction - this initializes lazy-loaded media
        return newsList.stream()
                .map(news -> {
                    // Force initialization of media collection
                    news.getMedia().size();
                    return convertToDto(news);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NewsDto> searchByKeyword(String keyword) {
        log.info("🔍 Searching news by keyword: '{}'", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            log.warn("⚠️ Empty keyword provided for search");
            return Collections.emptyList();
        }

        List<News> results = newsRepository.searchByKeyword(keyword.trim());
        log.info("✅ Found {} news items matching keyword '{}'", results.size(), keyword);

        return results.stream()
                .map(news -> {
                    news.getMedia().size(); // Initialize media
                    return convertToDto(news);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NewsDto> searchByTag(String tag) {
        log.info("🏷️ Searching news by tag: '{}'", tag);

        if (tag == null || tag.trim().isEmpty()) {
            log.warn("⚠️ Empty tag provided for search");
            return Collections.emptyList();
        }

        List<News> results = newsRepository.findByTag(tag.trim());
        log.info("✅ Found {} news items with tag '{}'", results.size(), tag);

        return results.stream()
                .map(news -> {
                    news.getMedia().size(); // Initialize media
                    return convertToDto(news);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NewsDto> searchByTags(Set<String> tags) {
        log.info("🏷️ Searching news by tags: {}", tags);

        if (tags == null || tags.isEmpty()) {
            log.warn("⚠️ Empty tags set provided for search");
            return Collections.emptyList();
        }

        List<String> searchTags = tags.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        List<News> results = newsRepository.findByTags(searchTags);
        log.info("✅ Found {} news items matching tags", results.size());

        return results.stream()
                .map(news -> {
                    news.getMedia().size(); // Initialize media
                    return convertToDto(news);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public NewsDto updateNews(Long newsId, NewsDto dto, MultipartFile newCoverImage,
                              List<MultipartFile> newMediaFiles, String performedBy) {
        log.info("✏️ Updating news - ID: {}", newsId);

        News news = newsRepository.findById(newsId)
                .orElseThrow(() -> new EntityNotFoundException("News not found: " + newsId));

        try {
            if (newCoverImage != null && !newCoverImage.isEmpty()) {
                String newCoverUrl = s3Service.upload(
                        newCoverImage.getBytes(),
                        newCoverImage.getOriginalFilename(),
                        newCoverImage.getContentType()
                );
                news.setCoverUrl(newCoverUrl);
                log.info("✅ Cover image updated on S3: {}", newCoverUrl);
            }

            if (dto.getDatePublished() != null) {
                news.setDatePublished(dto.getDatePublished());
            }

            updateBilingualContent(news, dto);
            updateCategoryAndSubCategory(news, dto);
            updateCollections(news, dto);
            updateMedia(news, dto, newMediaFiles);

            News updatedNews = newsRepository.save(news);
            log.info("✅ News updated successfully - ID: {}", updatedNews.getId());

            createAuditLog(updatedNews, "UPDATE", performedBy, "News updated");

            return convertToDto(updatedNews);

        } catch (IOException e) {
            log.error("❌ S3 upload failed during update", e);
            throw new BadRequestException("media.upload.failed", "Failed to upload files to S3");
        }
    }

    @Transactional
    public void deleteNews(Long newsId, String performedBy) {
        log.info("🗑️ Deleting news - ID: {}", newsId);

        News news = newsRepository.findById(newsId)
                .orElseThrow(() -> new EntityNotFoundException("News not found: " + newsId));

        createAuditLog(news, "DELETE", performedBy, "News deleted");
        newsRepository.delete(news);
        log.info("✅ News deleted successfully - ID: {}", newsId);
    }

    @Transactional
    public void deleteNewsBulk(List<Long> newsIds, String performedBy) {
        log.info("🗑️ Bulk deleting {} news items", newsIds.size());

        List<News> newsToDelete = newsRepository.findAllById(newsIds);

        if (newsToDelete.isEmpty()) {
            log.warn("⚠️ No news found for deletion");
            return;
        }

        List<NewsAuditLog> auditLogs = newsToDelete.stream()
                .map(news -> buildAuditLog(news, "DELETE", performedBy, "News deleted in bulk"))
                .collect(Collectors.toList());

        newsAuditLogRepository.saveAll(auditLogs);
        newsRepository.deleteAll(newsToDelete);
        log.info("✅ Successfully deleted {} news items", newsToDelete.size());
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private NewsCategory getOrCreateCategory(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            throw new BadRequestException("category.required", "Category name is required");
        }

        String trimmedName = categoryName.trim();
        return newsCategoryRepository.findByName(trimmedName)
                .orElseGet(() -> {
                    NewsCategory newCategory = NewsCategory.builder().name(trimmedName).build();
                    return newsCategoryRepository.save(newCategory);
                });
    }

    private NewsSubCategory getOrCreateSubCategory(String subCategoryName, NewsCategory category) {
        if (subCategoryName == null || subCategoryName.trim().isEmpty()) {
            throw new BadRequestException("subcategory.required", "SubCategory name is required");
        }

        String trimmedName = subCategoryName.trim();
        return newsSubCategoryRepository.findByCategoryAndName(category, trimmedName)
                .orElseGet(() -> {
                    NewsSubCategory newSubCategory = NewsSubCategory.builder()
                            .name(trimmedName)
                            .category(category)
                            .build();
                    return newsSubCategoryRepository.save(newSubCategory);
                });
    }

    private void setBilingualContent(News news, NewsDto dto) {
        if (dto.getCkbContent() != null) {
            news.setCkbContent(NewsContent.builder()
                    .title(dto.getCkbContent().getTitle())
                    .description(dto.getCkbContent().getDescription())
                    .build());
        }

        if (dto.getKmrContent() != null) {
            news.setKmrContent(NewsContent.builder()
                    .title(dto.getKmrContent().getTitle())
                    .description(dto.getKmrContent().getDescription())
                    .build());
        }
    }

    private void updateBilingualContent(News news, NewsDto dto) {
        if (dto.getCkbContent() != null) {
            if (news.getCkbContent() == null) {
                news.setCkbContent(new NewsContent());
            }
            news.getCkbContent().setTitle(dto.getCkbContent().getTitle());
            news.getCkbContent().setDescription(dto.getCkbContent().getDescription());
        }

        if (dto.getKmrContent() != null) {
            if (news.getKmrContent() == null) {
                news.setKmrContent(new NewsContent());
            }
            news.getKmrContent().setTitle(dto.getKmrContent().getTitle());
            news.getKmrContent().setDescription(dto.getKmrContent().getDescription());
        }
    }

    private void updateCategoryAndSubCategory(News news, NewsDto dto) {
        if (dto.getCategoryName() != null && !dto.getCategoryName().trim().isEmpty()) {
            NewsCategory category = getOrCreateCategory(dto.getCategoryName());
            news.setCategory(category);
        }

        if (dto.getSubCategoryName() != null && !dto.getSubCategoryName().trim().isEmpty()) {
            NewsSubCategory subCategory = getOrCreateSubCategory(dto.getSubCategoryName(), news.getCategory());
            news.setSubCategory(subCategory);
        }
    }

    private void updateCollections(News news, NewsDto dto) {
        if (dto.getTags() != null) {
            news.getTags().clear();
            news.getTags().addAll(dto.getTags());
        }

        if (dto.getKeywords() != null) {
            news.getKeywords().clear();
            news.getKeywords().addAll(dto.getKeywords());
        }
    }

    private void updateMedia(News news, NewsDto dto, List<MultipartFile> newMediaFiles) throws IOException {
        if (newMediaFiles != null && !newMediaFiles.isEmpty()) {
            news.getMedia().clear();
            List<NewsMedia> newMedia = processMediaFiles(newMediaFiles, news);
            news.getMedia().addAll(newMedia);
        }
    }

    private List<NewsMedia> processMediaFiles(List<MultipartFile> files, News news) throws IOException {
        List<NewsMedia> mediaList = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file.isEmpty()) continue;

            String mediaUrl = s3Service.upload(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType()
            );

            NewsMediaType mediaType = detectMediaType(file.getContentType());

            NewsMedia media = NewsMedia.builder()
                    .type(mediaType)
                    .url(mediaUrl)
                    .sortOrder(i)
                    .news(news)
                    .build();

            mediaList.add(media);
        }

        return mediaList;
    }

    private NewsMedia buildNewsMedia(NewsDto.MediaDto mediaDto, News news) {
        return NewsMedia.builder()
                .type(mediaDto.getType())
                .url(mediaDto.getUrl())
                .sortOrder(mediaDto.getSortOrder() != null ? mediaDto.getSortOrder() : 0)
                .news(news)
                .build();
    }

    private NewsMediaType detectMediaType(String contentType) {
        if (contentType == null) return NewsMediaType.IMAGE;

        String type = contentType.toLowerCase();
        if (type.startsWith("image/")) return NewsMediaType.IMAGE;
        if (type.startsWith("video/")) return NewsMediaType.VIDEO;
        if (type.startsWith("audio/")) return NewsMediaType.AUDIO;
        return NewsMediaType.DOCUMENT;
    }

    private void createAuditLog(News news, String action, String performedBy, String note) {
        NewsAuditLog auditLog = buildAuditLog(news, action, performedBy, note);
        newsAuditLogRepository.save(auditLog);
    }

    private NewsAuditLog buildAuditLog(News news, String action, String performedBy, String note) {
        return NewsAuditLog.builder()
                .news(news)
                .action(action)
                .performedBy(performedBy)
                .note(note)
                .build();
    }

    private NewsDto convertToDto(News news) {
        NewsDto dto = NewsDto.builder()
                .id(news.getId())
                .coverUrl(news.getCoverUrl())
                .datePublished(news.getDatePublished())
                .createdAt(news.getCreatedAt())
                .updatedAt(news.getUpdatedAt())
                .tags(new LinkedHashSet<>(news.getTags()))
                .keywords(new LinkedHashSet<>(news.getKeywords()))
                .categoryName(news.getCategory().getName())
                .subCategoryName(news.getSubCategory().getName())
                .build();

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

        // Media is already initialized via .size() call before this method
        if (news.getMedia() != null && !news.getMedia().isEmpty()) {
            List<NewsDto.MediaDto> mediaDtos = news.getMedia().stream()
                    .map(media -> NewsDto.MediaDto.builder()
                            .id(media.getId())
                            .type(media.getType())
                            .url(media.getUrl())
                            .sortOrder(media.getSortOrder())
                            .createdAt(media.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());
            dto.setMedia(mediaDtos);
        }

        return dto;
    }
}