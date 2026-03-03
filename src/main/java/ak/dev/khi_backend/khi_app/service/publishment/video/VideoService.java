package ak.dev.khi_backend.khi_app.service.publishment.video;

import ak.dev.khi_backend.khi_app.dto.publishment.video.VideoDTO;
import ak.dev.khi_backend.khi_app.dto.publishment.video.VideoMapper;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.exceptions.NotFoundException;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import ak.dev.khi_backend.khi_app.model.publishment.video.Video;
import ak.dev.khi_backend.khi_app.model.publishment.video.VideoClipItem;
import ak.dev.khi_backend.khi_app.model.publishment.video.VideoLog;
import ak.dev.khi_backend.khi_app.model.publishment.video.VideoType;
import ak.dev.khi_backend.khi_app.repository.publishment.topic.PublishmentTopicRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.video.VideoLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.video.VideoRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * سێرڤیسی ڤیدیۆ - بەڕێوەبردنی فیلم و کلیپە ڤیدیۆییەکان
 *
 * لیستی هەڵە کوردیەکان کە بەکاردێن:
 *
 * ١. "video.dto.required" = "زانیاری ڤیدیۆ پێویستە: DTO ناتوانرێت بەتاڵە بێت" (Bad Request)
 * ٢. "video.type.required" = "جۆری ڤیدیۆ پێویستە: دەبێت جۆر دیاری بکرێت (FILM یان CLIP)" (Bad Request)
 * ٣. "video.id.required" = "ئایدیی ڤیدیۆ پێویستە" (Bad Request)
 * ٤. "video.not_found" = "ڤیدیۆکە نەدۆزرایەوە: ئایدییەکە بوونی نییە لە سیستەم" (Not Found)
 * ٥. "video.cover.ckb.required" = "وێنەی بەرگی کوردیی ناوەندی پێویستە" (Bad Request)
 * ٦. "video.cover.kmr.required" = "وێنەی بەرگی کوردیی باکووری پێویستە" (Bad Request)
 * ٧. "video.cover.hover.required" = "وێنەی هاڤەر (hover) پێویستە" (Bad Request)
 * ٨. "video.clip.source.required" = "سەرچاوەی کلیپ پێویستە: هەر کلیپێک دەبێت لینکی ڕاستەقینە یان دەرەکی یان ئێمبێد هەبێت" (Bad Request)
 * ٩. "topic.not_found" = "بابەت نەدۆزرایەوە" (Not Found)
 * ١٠. "topic.type.mismatch" = "جۆری بابەت هەڵەیە: بابەتەکە بۆ VIDEO نییە" (Bad Request)
 * ١١. "video.topic.names.required" = "ناوی بابەت پێویستە: دەبێت لانیکەم ناوێکی کوردی بنووسرێت" (Bad Request)
 * ١٢. "search.keyword.required" = "کلیلەووشەی گەڕان پێویستە" (Bad Request)
 * ١٣. "search.tag.required" = "تاگی گەڕان پێویستە" (Bad Request)
 * ١٤. "media.upload.failed" = "شکستی ناردنی فایل: کێشە لە ناردنی فایل بۆ S3" (Bad Request)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private static final String TOPIC_ENTITY_TYPE = "VIDEO";
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private final VideoRepository            videoRepository;
    private final VideoLogRepository         videoLogRepository;
    private final PublishmentTopicRepository topicRepository;
    private final S3Service                  s3Service;

    // ═══════════════════════════════════════════════════════════════════════════
    // بابەت - دروستکردن، خوێندنەوە، سڕینەوە
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * دروستکردنی بابەتی ڤیدیۆ
     *
     * @throws BadRequestException - "ناوی بابەت پێویستە" (ئەگەر هەردووکی بەتاڵ بن)
     */
    @Transactional
    public VideoDTO.TopicView createTopic(String nameCkb, String nameKmr) {
        if (isBlank(nameCkb) && isBlank(nameKmr)) {
            // هەڵە: دەبێت لانیکەم ناوێکی کوردی بنووسرێت بۆ بابەت
            throw new BadRequestException("video.topic.names.required",
                    Map.of("message", "بابەت پێویستی بە لانیکەم ناوێکی کوردییە (ناوەندی یان باکوور)"));
        }
        PublishmentTopic topic = PublishmentTopic.builder()
                .entityType(TOPIC_ENTITY_TYPE)
                .nameCkb(trimOrNull(nameCkb))
                .nameKmr(trimOrNull(nameKmr))
                .build();
        PublishmentTopic saved = topicRepository.save(topic);
        log.info("بابەتی VIDEO دروستکرا id={} ckb='{}' kmr='{}'", saved.getId(), saved.getNameCkb(), saved.getNameKmr());
        return toTopicView(saved);
    }

    @Transactional(readOnly = true)
    public List<VideoDTO.TopicView> getTopics() {
        return topicRepository.findByEntityType(TOPIC_ENTITY_TYPE)
                .stream()
                .map(this::toTopicView)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VideoDTO.TopicView getTopicById(Long topicId) {
        return toTopicView(findTopicOrThrow(topicId));
    }

    /**
     * سڕینەوەی بابەت بە دابەزاندنی پەیوەندی لەگەڵ ڤیدیۆکان
     *
     * @throws NotFoundException - "بابەت نەدۆزرایەوە"
     */
    @Transactional
    public void deleteTopic(Long topicId) {
        PublishmentTopic topic = findTopicOrThrow(topicId);

        List<Video> linked = videoRepository.findByTopicId(topicId);
        for (Video v : linked) v.setTopic(null);

        if (!linked.isEmpty()) {
            videoRepository.saveAll(linked);
            log.info("بابەت id={} جیاکرایەوە لە {} ڤیدیۆ", topicId, linked.size());
        }

        topicRepository.delete(topic);
        log.info("بابەتی VIDEO سڕایەوە id={}", topicId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // دروستکردن (زیادکردن)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * دروستکردنی ڤیدیۆی نوێ
     *
     * @throws BadRequestException - "زانیاری ڤیدیۆ پێویستە"
     * @throws BadRequestException - "جۆری ڤیدیۆ پێویستە"
     * @throws BadRequestException - "وێنەی بەرگی کوردیی ناوەندی پێویستە"
     * @throws BadRequestException - "وێنەی بەرگی کوردیی باکووری پێویستە"
     * @throws BadRequestException - "وێنەی هاڤەر پێویستە"
     * @throws BadRequestException - "سەرچاوەی کلیپ پێویستە"
     * @throws BadRequestException - "شکستی ناردنی فایل"
     */
    @Transactional
    public VideoDTO addVideo(
            VideoDTO dto,
            MultipartFile ckbCoverImage,
            MultipartFile kmrCoverImage,
            MultipartFile hoverImage,
            MultipartFile videoFile
    ) {
        requireDto(dto);

        // ✅ دانانی ٣ وێنەی بەرگ بە سەربەخۆیی
        String ckbUrl = resolveCoverUrl(dto.getCkbCoverUrl(), ckbCoverImage);
        if (isBlank(ckbUrl)) {
            // هەڵە: وێنەی بەرگی کوردیی ناوەندی (سۆرانی) پێویستە
            throw new BadRequestException("video.cover.ckb.required",
                    Map.of("field", "ckbCoverImage یا ckbCoverUrl"));
        }

        String kmrUrl = resolveCoverUrl(dto.getKmrCoverUrl(), kmrCoverImage);
        if (isBlank(kmrUrl)) {
            // هەڵە: وێنەی بەرگی کوردیی باکووری (کورمانجی) پێویستە
            throw new BadRequestException("video.cover.kmr.required",
                    Map.of("field", "kmrCoverImage یا kmrCoverUrl"));
        }

        String hoverUrl = resolveCoverUrl(dto.getHoverCoverUrl(), hoverImage);
        if (isBlank(hoverUrl)) {
            // هەڵە: وێنەی هاڤەر (hover) پێویستە
            throw new BadRequestException("video.cover.hover.required",
                    Map.of("field", "hoverImage یا hoverCoverUrl"));
        }

        Video video = VideoMapper.toEntity(dto);
        video.setCkbCoverUrl(ckbUrl);
        video.setKmrCoverUrl(kmrUrl);
        video.setHoverCoverUrl(hoverUrl);

        // بابەت
        video.setTopic(resolveOrCreateTopic(dto.getTopicId(), dto.getNewTopic()));

        enforceAlbumRule(video, dto);

        if (video.getVideoType() == VideoType.FILM) {
            applyVideoSource(video, dto, videoFile);
            if (video.getVideoClipItems() != null) video.getVideoClipItems().clear();
        } else {
            clearFilmSourceFields(video);
            buildAndAttachClipItems(video, dto);
        }

        Video saved = videoRepository.save(video);

        logAction(saved.getId(), getTitle(saved), "CREATED",
                "ڤیدیۆ دروستکرا: جۆر=" + saved.getVideoType() + ", ئەلبومی بیرەوەری=" + saved.isAlbumOfMemories());

        return VideoMapper.toDTO(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // خوێندنەوە
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<VideoDTO> getAllVideos(int page, int size) {
        return videoRepository.findAll(buildPageable(page, size)).map(VideoMapper::toDTO);
    }

    /**
     * هێنانی ڤیدیۆ بەپێی ئایدی
     *
     * @throws NotFoundException - "ڤیدیۆکە نەدۆزرایەوە"
     * @throws BadRequestException - "ئایدیی ڤیدیۆ پێویستە"
     */
    @Transactional(readOnly = true)
    public VideoDTO getVideoById(Long id) {
        return VideoMapper.toDTO(findOrThrow(id));
    }

    /**
     * گەڕانی ڤیدیۆ بەپێی کلیلەووشە
     *
     * @throws BadRequestException - "کلیلەووشەی گەڕان پێویستە"
     */
    @Transactional(readOnly = true)
    public Page<VideoDTO> searchByKeyword(String keyword, int page, int size) {
        String normalized = normalizeRequiredSearch(keyword, "keyword");
        return videoRepository.searchByKeyword(normalized, buildPageable(page, size)).map(VideoMapper::toDTO);
    }

    /**
     * گەڕانی ڤیدیۆ بەپێی تاگ
     *
     * @throws BadRequestException - "تاگی گەڕان پێویستە"
     */
    @Transactional(readOnly = true)
    public Page<VideoDTO> searchByTag(String tag, int page, int size) {
        String normalized = normalizeRequiredSearch(tag, "tag");
        return videoRepository.searchByTag(normalized, buildPageable(page, size)).map(VideoMapper::toDTO);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // نوێکردنەوە
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * نوێکردنەوەی زانیاری ڤیدیۆ
     *
     * @throws BadRequestException    - "زانیاری ڤیدیۆ پێویستە"
     * @throws BadRequestException    - "ئایدیی ڤیدیۆ پێویستە"
     * @throws NotFoundException      - "ڤیدیۆکە نەدۆزرایەوە"
     * @throws BadRequestException    - "شکستی ناردنی فایل"
     */
    @Transactional
    public VideoDTO updateVideo(
            Long id,
            VideoDTO dto,
            MultipartFile ckbCoverImage,
            MultipartFile kmrCoverImage,
            MultipartFile hoverImage,
            MultipartFile videoFile
    ) {
        requireDto(dto);

        Video video = findOrThrow(id);
        VideoMapper.updateEntity(video, dto);

        // ✅ نوێکردنەوەی ٣ وێنەی بەرگ بە سەربەخۆیی (تەنها ئەگەن نێردرابن)
        applyCoverUpdate(video, dto, ckbCoverImage, kmrCoverImage, hoverImage);

        // بابەت: clearTopic > topicId > newTopic
        if (dto.isClearTopic()) {
            video.setTopic(null);
        } else if (dto.getTopicId() != null || dto.getNewTopic() != null) {
            video.setTopic(resolveOrCreateTopic(dto.getTopicId(), dto.getNewTopic()));
        }

        enforceAlbumRule(video, dto);

        if (video.getVideoType() == VideoType.FILM) {
            clearClipItems(video);
            applyVideoSourceForUpdate(video, dto, videoFile);
        } else {
            clearFilmSourceFields(video);
            if (dto.getVideoClipItems() != null) {
                clearClipItems(video);
                buildAndAttachClipItems(video, dto);
            }
        }

        Video updated = videoRepository.save(video);
        logAction(updated.getId(), getTitle(updated), "UPDATED", "ڤیدیۆ نوێکرایەوە");
        return VideoMapper.toDTO(updated);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // سڕینەوە
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * سڕینەوەی ڤیدیۆ بە تەواوی
     *
     * @throws BadRequestException - "ئایدیی ڤیدیۆ پێویستە"
     * @throws NotFoundException   - "ڤیدیۆکە نەدۆزرایەوە"
     */
    @Transactional
    public void deleteVideo(Long id) {
        Video video = findOrThrow(id);
        String title = getTitle(video);
        videoRepository.delete(video);
        logAction(id, title, "DELETED", "ڤیدیۆ بە تەواوی سڕایەوە");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // یاریدەدەرەکانی بابەت
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * ڕیزبەندی چارەسەرکردن:
     *  ١. topicId هەبێت → دۆزینەوەی بابەتی بەرەمە
     *  ٢. newTopic هەبێت → دروستکردنی بابەتی نوێ
     *  ٣. هیچیان نەبێت → null
     *
     * @throws NotFoundException   - "بابەت نەدۆزرایەوە"
     * @throws BadRequestException - "جۆری بابەت هەڵەیە"
     * @throws BadRequestException - "ناوی بابەت پێویستە"
     */
    private PublishmentTopic resolveOrCreateTopic(Long topicId, VideoDTO.InlineTopicRequest newTopic) {
        if (topicId != null) return findTopicOrThrow(topicId);

        if (newTopic != null) {
            if (isBlank(newTopic.getNameCkb()) && isBlank(newTopic.getNameKmr())) {
                // هەڵە: دەبێت لانیکەم ناوێکی کوردی بنووسرێت
                throw new BadRequestException("video.topic.names.required",
                        Map.of("message", "بابەتی نوێ پێویستی بە لانیکەم ناوێکی کوردییە"));
            }
            PublishmentTopic created = topicRepository.save(
                    PublishmentTopic.builder()
                            .entityType(TOPIC_ENTITY_TYPE)
                            .nameCkb(trimOrNull(newTopic.getNameCkb()))
                            .nameKmr(trimOrNull(newTopic.getNameKmr()))
                            .build()
            );
            log.info("بابەتی VIDEO دروستکرا inline id={}", created.getId());
            return created;
        }
        return null;
    }

    private PublishmentTopic findTopicOrThrow(Long topicId) {
        PublishmentTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> {
                    // هەڵە: بابەتەکە نەدۆزرایەوە
                    return new NotFoundException("topic.not_found", Map.of("id", topicId));
                });
        if (!Objects.equals(TOPIC_ENTITY_TYPE, topic.getEntityType())) {
            // هەڵە: بابەتەکە بۆ VIDEO نییە
            throw new BadRequestException("topic.type.mismatch",
                    Map.of("message", "بابەت id=" + topicId + " بۆ '" + topic.getEntityType() +
                            "'ە، چاوەڕوان دەکرێت 'VIDEO' بێت"));
        }
        return topic;
    }

    private VideoDTO.TopicView toTopicView(PublishmentTopic t) {
        return VideoDTO.TopicView.builder()
                .id(t.getId())
                .nameCkb(t.getNameCkb())
                .nameKmr(t.getNameKmr())
                .createdAt(t.getCreatedAt())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // یاریدەدەرەکانی جۆر و ڕێساکان
    // ═══════════════════════════════════════════════════════════════════════════

    private void enforceAlbumRule(Video video, VideoDTO dto) {
        if (video.getVideoType() == VideoType.FILM) {
            video.setAlbumOfMemories(false);
            return;
        }
        if (dto.getAlbumOfMemories() != null) {
            video.setAlbumOfMemories(Boolean.TRUE.equals(dto.getAlbumOfMemories()));
        }
    }

    private void clearFilmSourceFields(Video video) {
        video.setSourceUrl(null);
        video.setSourceExternalUrl(null);
        video.setSourceEmbedUrl(null);
    }

    private void clearClipItems(Video video) {
        if (video.getVideoClipItems() != null) video.getVideoClipItems().clear();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // یاریدەدەرەکانی کلیپ
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * دروستکردن و لکاندنی مادەکانی کلیپ
     *
     * @throws BadRequestException - "سەرچاوەی کلیپ پێویستە"
     */
    private void buildAndAttachClipItems(Video video, VideoDTO dto) {
        if (dto == null || dto.getVideoClipItems() == null || dto.getVideoClipItems().isEmpty()) return;

        for (VideoDTO.VideoClipItemDTO clipDto : dto.getVideoClipItems()) {
            if (clipDto == null) continue;
            if (isBlank(clipDto.getUrl()) && isBlank(clipDto.getExternalUrl()) && isBlank(clipDto.getEmbedUrl())) {
                // هەڵە: هەر کلیپێک دەبێت لانیکەم یەک سەرچاوەی هەبێت
                throw new BadRequestException("video.clip.source.required",
                        Map.of("message", "هەر کلیپێک پێویستی بە لینکی ڕاستەقینە یان دەرەکی یان ئێمبێد هەیە"));
            }

            VideoClipItem item = VideoClipItem.builder()
                    .url(trimOrNull(clipDto.getUrl()))
                    .externalUrl(trimOrNull(clipDto.getExternalUrl()))
                    .embedUrl(trimOrNull(clipDto.getEmbedUrl()))
                    .clipNumber(clipDto.getClipNumber())
                    .durationSeconds(clipDto.getDurationSeconds())
                    .resolution(trimOrNull(clipDto.getResolution()))
                    .fileFormat(trimOrNull(clipDto.getFileFormat()))
                    .fileSizeMb(clipDto.getFileSizeMb())
                    .titleCkb(trimOrNull(clipDto.getTitleCkb()))
                    .titleKmr(trimOrNull(clipDto.getTitleKmr()))
                    .descriptionCkb(trimOrNull(clipDto.getDescriptionCkb()))
                    .descriptionKmr(trimOrNull(clipDto.getDescriptionKmr()))
                    .build();

            video.addClipItem(item);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // یاریدەدەرەکانی سەرچاوە و وێنەی بەرگ
    // ═══════════════════════════════════════════════════════════════════════════

    private void applyVideoSource(Video video, VideoDTO dto, MultipartFile videoFile) {
        if (videoFile != null && !videoFile.isEmpty()) {
            video.setSourceUrl(uploadToS3(videoFile));
            video.setSourceExternalUrl(null);
            video.setSourceEmbedUrl(null);
            return;
        }

        String url = dto != null ? trimOrNull(dto.getSourceUrl()) : null;
        String ext = dto != null ? trimOrNull(dto.getSourceExternalUrl()) : null;
        String emb = dto != null ? trimOrNull(dto.getSourceEmbedUrl()) : null;

        if (!isBlank(url) || !isBlank(ext) || !isBlank(emb)) {
            video.setSourceUrl(url);
            video.setSourceExternalUrl(ext);
            video.setSourceEmbedUrl(emb);
        }
    }

    private void applyVideoSourceForUpdate(Video video, VideoDTO dto, MultipartFile videoFile) {
        if (videoFile != null && !videoFile.isEmpty()) {
            video.setSourceUrl(uploadToS3(videoFile));
            video.setSourceExternalUrl(null);
            video.setSourceEmbedUrl(null);
            return;
        }

        boolean touched = dto != null && (dto.getSourceUrl() != null || dto.getSourceExternalUrl() != null || dto.getSourceEmbedUrl() != null);
        if (!touched) return;

        video.setSourceUrl(trimOrNull(dto.getSourceUrl()));
        video.setSourceExternalUrl(trimOrNull(dto.getSourceExternalUrl()));
        video.setSourceEmbedUrl(trimOrNull(dto.getSourceEmbedUrl()));
    }

    /**
     * دانانی وێنەی بەرگ: فایل لەسەر لینک پێشەکییە
     */
    private String resolveCoverUrl(String urlFromDto, MultipartFile coverFile) {
        if (coverFile != null && !coverFile.isEmpty()) return uploadToS3(coverFile);
        if (!isBlank(urlFromDto)) return urlFromDto.trim();
        return null;
    }

    /**
     * نوێکردنەوەی ٣ وێنەی بەرگ بە سەربەخۆیی
     */
    private void applyCoverUpdate(Video video, VideoDTO dto,
                                  MultipartFile ckbCoverImage,
                                  MultipartFile kmrCoverImage,
                                  MultipartFile hoverImage) {

        String ckb = resolveCoverUrl(dto.getCkbCoverUrl(), ckbCoverImage);
        if (!isBlank(ckb)) video.setCkbCoverUrl(ckb);

        String kmr = resolveCoverUrl(dto.getKmrCoverUrl(), kmrCoverImage);
        if (!isBlank(kmr)) video.setKmrCoverUrl(kmr);

        String hov = resolveCoverUrl(dto.getHoverCoverUrl(), hoverImage);
        if (!isBlank(hov)) video.setHoverCoverUrl(hov);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // یاریدەدەرەکانی ڕیپۆزیتۆری و تۆمارکردن
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * دۆزینەوەی ڤیدیۆ یان هەڵەدان
     *
     * @throws BadRequestException - "ئایدیی ڤیدیۆ پێویستە"
     * @throws NotFoundException   - "ڤیدیۆکە نەدۆزرایەوە"
     */
    private Video findOrThrow(Long id) {
        if (id == null) {
            // هەڵە: ئایدی پێویستە
            throw new BadRequestException("video.id.required", Map.of("field", "id"));
        }
        return videoRepository.findById(id)
                .orElseThrow(() -> {
                    // هەڵە: ڤیدیۆکە نەدۆزرایەوە
                    return new NotFoundException("video.not_found", Map.of("id", id));
                });
    }

    /**
     * ناردنی فایل بۆ S3
     *
     * @throws BadRequestException - "شکستی ناردنی فایل"
     */
    private String uploadToS3(MultipartFile file) {
        try {
            return s3Service.upload(file.getBytes(), file.getOriginalFilename(), file.getContentType());
        } catch (IOException e) {
            // هەڵە: کێشە لە ناردنی فایل
            throw new BadRequestException("media.upload.failed",
                    Map.of("filename", file.getOriginalFilename(), "error", e.getMessage()));
        }
    }

    private void logAction(Long videoId, String videoTitle, String action, String details) {
        videoLogRepository.save(VideoLog.builder()
                .videoId(videoId)
                .videoTitle(videoTitle)
                .action(action)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build());
    }

    private Pageable buildPageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), DEFAULT_SORT);
    }

    /**
     * پشتڕاستکردنەوەی گەڕان
     *
     * @throws BadRequestException - ئەگەر بەتاڵ بێت (بەپێی جۆر: "کلیلەووشەی گەڕان پێویستە" یان "تاگی گەڕان پێویستە")
     */
    private String normalizeRequiredSearch(String value, String fieldName) {
        String normalized = trimOrNull(value);
        if (isBlank(normalized)) {
            String errorCode = "keyword".equals(fieldName) ? "search.keyword.required" : "search.tag.required";
            throw new BadRequestException(errorCode, Map.of("field", fieldName));
        }
        return normalized;
    }

    /**
     * پشتڕاستکردنەوەی DTO
     *
     * @throws BadRequestException - "زانیاری ڤیدیۆ پێویستە"
     * @throws BadRequestException - "جۆری ڤیدیۆ پێویستە"
     */
    private void requireDto(VideoDTO dto) {
        if (dto == null) {
            // هەڵە: DTO بەتاڵە
            throw new BadRequestException("video.dto.required", Map.of("field", "dto"));
        }
        if (dto.getVideoType() == null) {
            // هەڵە: جۆری ڤیدیۆ پێویستە
            throw new BadRequestException("video.type.required", Map.of("field", "videoType"));
        }
    }

    private String getTitle(Video video) {
        if (video.getCkbContent() != null && !isBlank(video.getCkbContent().getTitle()))
            return video.getCkbContent().getTitle();
        if (video.getKmrContent() != null && !isBlank(video.getKmrContent().getTitle()))
            return video.getKmrContent().getTitle();
        return "ڤیدیۆی بێ ناو";
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}