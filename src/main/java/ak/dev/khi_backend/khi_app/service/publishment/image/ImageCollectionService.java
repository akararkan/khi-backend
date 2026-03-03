package ak.dev.khi_backend.khi_app.service.publishment.image;

import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionDTO.*;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.ImageCollectionType;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.exceptions.NotFoundException;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageAlbumItem;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageCollection;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageCollectionLog;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageContent;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import ak.dev.khi_backend.khi_app.repository.publishment.image.ImageCollectionLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.image.ImageCollectionRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.topic.PublishmentTopicRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * سێرڤیسی کۆمەڵەی وێنە - بەڕێوەبردنی کۆلێکشنی وێنە، گەلێری، و چیرۆکی وێنەیی
 *
 * لیستی هەڵە کوردیەکان کە بەکاردێن:
 *
 * ١. "error.validation" = "هەڵەی پشتڕاستکردنەوە: زانیاری نادروستە یان پاکە" (Bad Request)
 * ٢. "media.upload.failed" = "شکستی ناردنی فایل: کێشە لە ناردنی وێنە بۆ سێرڤەری S3" (Bad Request)
 * ٣. "image.source.required" = "سەرچاوەی وێنە پێویستە: دەبێت فایل یان لینک (URL) دابنرێت" (Bad Request)
 * ٤. "imageCollection.not_found" = "کۆمەڵەی وێنە نەدۆزرایەوە: ئایدییەکە بوونی نییە لە سیستەم" (Not Found)
 * ٥. "imageCollection.single.invalid" = "هەڵەی جۆری تاک: جۆری SINGLE تەنها یەک وێنە دەقبوڵێت" (Bad Request)
 * ٦. "imageCollection.gallery.invalid" = "هەڵەی گەلێری: جۆری GALLERY پێویستی بە لانیکەم یەک وێنەیە" (Bad Request)
 * ٧. "imageCollection.photoStory.invalid" = "هەڵەی چیرۆکی وێنەیی: جۆری PHOTO_STORY پێویستی بە لانیکەم دوو وێنەیە" (Bad Request)
 * ٨. "imageCollection.type.required" = "جۆری کۆمەڵە پێویستە: دەبێت جۆر دیاری بکرێت (SINGLE, GALLERY, PHOTO_STORY)" (Bad Request)
 * ٩. "imageCollection.languages.required" = "زمانەکانی ناوەڕۆک پێویستە: دەبێت لانیکەم یەک زمان هەڵبژێردرێت" (Bad Request)
 * ١٠. "imageCollection.cover.required" = "وێنەی بەرگ پێویستە: دەبێت لانیکەم یەک وێنەی بەرگ دیاری بکرێت" (Bad Request)
 * ١١. "topic.not_found" = "بابەت نەدۆزرایەوە: ئایدیی بابەت بوونی نییە" (Not Found)
 * ١٢. "topic.type.mismatch" = "جۆری بابەت هەڵەیە: بابەتەکە بۆ IMAGE نییە" (Bad Request)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCollectionService {

    /** ناوی جۆری بابەت بۆ کۆمەڵەی وێنە */
    private static final String TOPIC_ENTITY_TYPE = "IMAGE";

    private final ImageCollectionRepository    imageCollectionRepository;
    private final ImageCollectionLogRepository imageCollectionLogRepository;
    private final PublishmentTopicRepository   topicRepository;
    private final S3Service                    s3Service;

    // =========================================================================
    // دروستکردن
    // =========================================================================

    /**
     * دروستکردنی کۆمەڵەی وێنەی نوێ
     *
     * @param dto              زانیاری JSON
     * @param ckbCoverImage    وێنەی بەرگی کوردیی ناوەندی (ئارەزوومەندانە)
     * @param kmrCoverImage    وێنەی بەرگی کوردیی باکووری (ئارەزوومەندانە)
     * @param hoverCoverImage  وێنەی بەرگی هاڤەر (ئارەزوومەندانە)
     * @param images           وێنەکانی ئەلبوم (ئارەزوومەندانە)
     *
     * @throws BadRequestException    - زانیاری نادروست ("هەڵەی پشتڕاستکردنەوە")
     * @throws BadRequestException    - جۆر دیاری نەکراوە ("جۆری کۆمەڵە پێویستە")
     * @throws BadRequestException    - زمانی نە دیاری کراو ("زمانەکانی ناوەڕۆک پێویستە")
     * @throws BadRequestException    - وێنەی بەرگ نە دیاری کراو ("وێنەی بەرگ پێویستە")
     * @throws BadRequestException    - ژمارەی وێنە نادروست بۆ جۆرە خۆراوەکان
     * @throws BadRequestException    - سەرچاوەی وێنە نە دیاری کراو ("سەرچاوەی وێنە پێویستە")
     * @throws BadRequestException    - کێشە لە ناردنی فایل ("شکستی ناردنی فایل")
     */
    @Transactional
    public Response create(
            CreateRequest dto,
            MultipartFile ckbCoverImage,
            MultipartFile kmrCoverImage,
            MultipartFile hoverCoverImage,
            List<MultipartFile> images
    ) {
        validateCreate(dto, ckbCoverImage);

        try {
            // ── دانانی وێنەی بەرگ (فایل لەسەر لینک پێشەکییە) ───────────
            String ckbCoverUrl   = resolveCoverUrl(dto.getCkbCoverUrl(),   ckbCoverImage);
            String kmrCoverUrl   = resolveCoverUrl(dto.getKmrCoverUrl(),   kmrCoverImage);
            String hoverCoverUrl = resolveCoverUrl(dto.getHoverCoverUrl(), hoverCoverImage);

            // ── دۆزینەوە یان دروستکردنی بابەت ─────────────────────────────────
            PublishmentTopic topic = resolveOrCreateTopic(dto.getTopicId(), dto.getNewTopic());

            // ── دروستکردنی ئینتیتی ──────────────────────────────────────────
            ImageCollection entity = ImageCollection.builder()
                    .collectionType(dto.getCollectionType())
                    .ckbCoverUrl(ckbCoverUrl)
                    .kmrCoverUrl(kmrCoverUrl)
                    .hoverCoverUrl(hoverCoverUrl)
                    .topic(topic)
                    .publishmentDate(dto.getPublishmentDate())
                    .contentLanguages(new LinkedHashSet<>(safeLangs(dto.getContentLanguages())))
                    .tagsCkb(new LinkedHashSet<>(safeSet(dto.getTags()     != null ? dto.getTags().getCkb()     : null)))
                    .tagsKmr(new LinkedHashSet<>(safeSet(dto.getTags()     != null ? dto.getTags().getKmr()     : null)))
                    .keywordsCkb(new LinkedHashSet<>(safeSet(dto.getKeywords() != null ? dto.getKeywords().getCkb() : null)))
                    .keywordsKmr(new LinkedHashSet<>(safeSet(dto.getKeywords() != null ? dto.getKeywords().getKmr() : null)))
                    .build();

            applyContentByLanguages(entity, dto.getContentLanguages(), dto.getCkbContent(), dto.getKmrContent());

            // ── دروستکردنی مادەکانی ئەلبوم ─────────────────────────────────────
            List<ImageAlbumItem> builtItems = buildAlbumItems(
                    entity, dto.getCollectionType(), dto.getImageAlbum(), images);
            entity.getImageAlbum().clear();
            entity.getImageAlbum().addAll(builtItems);

            ImageCollection saved = imageCollectionRepository.save(entity);
            createLog(saved.getId(), titleOf(saved), "CREATE",
                    "کۆمەڵەی وێنە دروستکرا — جۆر=" + saved.getCollectionType()
                            + (topic != null ? " بابەتid=" + topic.getId() : ""));

            saved.getImageAlbum().size(); // init LAZY collection
            return toResponse(saved);

        } catch (IOException e) {
            // هەڵە: کێشە لە ناردنی وێنە بۆ S3
            throw new BadRequestException("media.upload.failed",
                    Map.of("reason", "کێشە لە ناردنی وێنە: " + e.getMessage()));
        }
    }

    // =========================================================================
    // نوێکردنەوە
    // =========================================================================

    /**
     * نوێکردنەوەی کۆمەڵەی وێنە (تەنها خانە/فایلە نێردراوەکان دەگۆڕدرێن)
     *
     * @throws BadRequestException    - ئایدی بەتاڵە ("هەڵەی پشتڕاستکردنەوە")
     * @throws NotFoundException      - کۆمەڵەکە نەدۆزرایەوە ("کۆمەڵەی وێنە نەدۆزرایەوە")
     * @throws BadRequestException    - هەر هەڵەیەکی دیکە لە پشتڕاستکردنەوە
     * @throws BadRequestException    - کێشە لە ناردنی فایل
     */
    @Transactional
    public Response update(
            Long id,
            UpdateRequest dto,
            MultipartFile ckbCoverImage,
            MultipartFile kmrCoverImage,
            MultipartFile hoverCoverImage,
            List<MultipartFile> images
    ) {
        if (id == null) {
            // هەڵە: ئایدیی کۆمەڵە پێویستە بۆ نوێکردنەوە
            throw new BadRequestException("error.validation", Map.of("field", "id", "message", "ئایدی پێویستە"));
        }

        ImageCollection entity = imageCollectionRepository.findById(id)
                .orElseThrow(() -> {
                    // هەڵە: کۆمەڵەی وێنە نەدۆزرایەوە لە بنکەدراو
                    return new NotFoundException("imageCollection.not_found", Map.of("id", id));
                });

        try {
            // ── جۆری کۆمەڵە ───────────────────────────────────────────────
            if (dto.getCollectionType() != null) {
                entity.setCollectionType(dto.getCollectionType());
            }

            // ── وێنەی بەرگ: فایل > لینکی DTO > هێشتنەوەی کۆن ─────────
            if (hasFile(ckbCoverImage)) {
                entity.setCkbCoverUrl(uploadFile(ckbCoverImage));
            } else if (!isBlank(dto.getCkbCoverUrl())) {
                entity.setCkbCoverUrl(dto.getCkbCoverUrl().trim());
            }

            if (hasFile(kmrCoverImage)) {
                entity.setKmrCoverUrl(uploadFile(kmrCoverImage));
            } else if (!isBlank(dto.getKmrCoverUrl())) {
                entity.setKmrCoverUrl(dto.getKmrCoverUrl().trim());
            }

            if (hasFile(hoverCoverImage)) {
                entity.setHoverCoverUrl(uploadFile(hoverCoverImage));
            } else if (!isBlank(dto.getHoverCoverUrl())) {
                entity.setHoverCoverUrl(dto.getHoverCoverUrl().trim());
            }

            // ── بابەت: clearTopic > topicId > newTopic ────────────────────
            if (dto.isClearTopic()) {
                entity.setTopic(null);
            } else if (dto.getTopicId() != null || dto.getNewTopic() != null) {
                entity.setTopic(resolveOrCreateTopic(dto.getTopicId(), dto.getNewTopic()));
            }

            // ── بەرواری بڵاوکردنەوە ────────────────────────────────────────
            if (dto.getPublishmentDate() != null) {
                entity.setPublishmentDate(dto.getPublishmentDate());
            }

            // ── زمانەکان ───────────────────────────────────────────────────
            if (dto.getContentLanguages() != null) {
                entity.getContentLanguages().clear();
                entity.getContentLanguages().addAll(safeLangs(dto.getContentLanguages()));
            }

            // ── ناوەڕۆک ────────────────────────────────────────────────────
            applyContentByLanguages(entity, entity.getContentLanguages(),
                    dto.getCkbContent(), dto.getKmrContent());

            // ── تاگەکان ─────────────────────────────────────────────────────
            if (dto.getTags() != null) {
                if (dto.getTags().getCkb() != null) {
                    entity.getTagsCkb().clear();
                    entity.getTagsCkb().addAll(cleanStrings(dto.getTags().getCkb()));
                }
                if (dto.getTags().getKmr() != null) {
                    entity.getTagsKmr().clear();
                    entity.getTagsKmr().addAll(cleanStrings(dto.getTags().getKmr()));
                }
            }

            // ── کلیلەووشەکان ────────────────────────────────────────────────
            if (dto.getKeywords() != null) {
                if (dto.getKeywords().getCkb() != null) {
                    entity.getKeywordsCkb().clear();
                    entity.getKeywordsCkb().addAll(cleanStrings(dto.getKeywords().getCkb()));
                }
                if (dto.getKeywords().getKmr() != null) {
                    entity.getKeywordsKmr().clear();
                    entity.getKeywordsKmr().addAll(cleanStrings(dto.getKeywords().getKmr()));
                }
            }

            // ── مادەکانی ئەلبوم: تەنها کاتێک دەگۆڕدرێن کە دیاری کراون ───────
            boolean hasUploads = images != null
                    && images.stream().anyMatch(f -> f != null && !f.isEmpty());
            if (dto.getImageAlbum() != null || hasUploads) {
                entity.getImageAlbum().clear();
                List<ImageAlbumItem> rebuilt = buildAlbumItems(
                        entity, entity.getCollectionType(), dto.getImageAlbum(), images);
                entity.getImageAlbum().addAll(rebuilt);
            }

            ImageCollection saved = imageCollectionRepository.save(entity);
            createLog(saved.getId(), titleOf(saved), "UPDATE",
                    "کۆمەڵەی وێنە نوێکرایەوە — جۆر=" + saved.getCollectionType());

            saved.getImageAlbum().size();
            return toResponse(saved);

        } catch (IOException e) {
            throw new BadRequestException("media.upload.failed",
                    Map.of("reason", "کێشە لە ناردنی وێنە: " + e.getMessage()));
        }
    }

    // =========================================================================
    // هێنانی هەموو کۆمەڵەکان
    // =========================================================================

    @Transactional(readOnly = true)
    public List<Response> getAll() {
        return imageCollectionRepository.findAll().stream()
                .map(c -> { c.getImageAlbum().size(); return toResponse(c); })
                .collect(Collectors.toList());
    }

    // =========================================================================
    // سڕینەوە
    // =========================================================================

    /**
     * سڕینەوەی کۆمەڵەی وێنە
     *
     * @throws NotFoundException - کۆمەڵەکە نەدۆزرایەوە ("کۆمەڵەی وێنە نەدۆزرایەوە")
     */
    @Transactional
    public void delete(Long id) {
        ImageCollection entity = imageCollectionRepository.findById(id)
                .orElseThrow(() -> {
                    // هەڵە: کۆمەڵەی وێنە نەدۆزرایەوە بۆ سڕینەوە
                    return new NotFoundException("imageCollection.not_found", Map.of("id", id));
                });
        createLog(entity.getId(), titleOf(entity), "DELETE",
                "کۆمەڵەی وێنە سڕایەوە — جۆر=" + entity.getCollectionType());
        imageCollectionRepository.delete(entity);
    }

    // =========================================================================
    // یاریدەدەرەکانی بابەت
    // =========================================================================

    /**
     * ڕیزبەندی چارەسەرکردن:
     *   ١. ئەگەر topicId هەبێت → دۆزینەوەی بابەتی IMAGE (هەڵە ئەگەر جۆری هەڵەبێت)
     *   ٢. ئەگەر newTopic هەبێت → دروستکردنی بابەتی IMAGEی نوێ
     *   ٣. هیچیان نەبێت → null
     *
     * @throws NotFoundException   - بابەت نەدۆزرایەوە ("بابەت نەدۆزرایەوە")
     * @throws BadRequestException - جۆری بابەت هەڵەیە ("جۆری بابەت هەڵەیە")
     */
    private PublishmentTopic resolveOrCreateTopic(Long topicId, InlineTopicRequest newTopic) {
        if (topicId != null) {
            return findImageTopicOrThrow(topicId);
        }
        if (newTopic != null) {
            if (isBlank(newTopic.getNameCkb()) && isBlank(newTopic.getNameKmr())) {
                // هەڵە: دەبێت لانیکەم ناوێک بە کوردی بنووسرێت
                throw new BadRequestException("error.validation",
                        Map.of("message", "بابەتی نوێ پێویستی بە لانیکەم ناوێکی کوردییە (ناوەندی یان باکوور)"));
            }
            PublishmentTopic created = topicRepository.save(
                    PublishmentTopic.builder()
                            .entityType(TOPIC_ENTITY_TYPE)
                            .nameCkb(trimOrNull(newTopic.getNameCkb()))
                            .nameKmr(trimOrNull(newTopic.getNameKmr()))
                            .build()
            );
            log.info("بابەتی IMAGE دروستکرا inline id={}", created.getId());
            return created;
        }
        return null;
    }

    private PublishmentTopic findImageTopicOrThrow(Long topicId) {
        PublishmentTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> {
                    // هەڵە: بابەتەکە نەدۆزرایەوە
                    return new NotFoundException("topic.not_found", Map.of("id", topicId));
                });
        if (!TOPIC_ENTITY_TYPE.equals(topic.getEntityType())) {
            // هەڵە: بابەتەکە بۆ IMAGE نییە
            throw new BadRequestException("topic.type.mismatch", Map.of(
                    "message", "بابەت id=" + topicId
                            + " بۆ '" + topic.getEntityType()
                            + "'ە، چاوەڕوان دەکرێت 'IMAGE' بێت"));
        }
        return topic;
    }

    // =========================================================================
    // یاریدەدەرەکانی وێنەی بەرگ
    // =========================================================================

    /**
     * دانانی وێنەی بەرگ: فایل لەسەر لینک پێشەکییە.
     * null دەگەڕێنێتەوە کاتێک هیچیان نەبێت (ئەوی کۆن هەڵدەگیرێت).
     */
    private String resolveCoverUrl(String dtoUrl, MultipartFile file) throws IOException {
        if (hasFile(file)) return uploadFile(file);
        return isBlank(dtoUrl) ? null : dtoUrl.trim();
    }

    private boolean hasFile(MultipartFile f) {
        return f != null && !f.isEmpty();
    }

    private String uploadFile(MultipartFile f) throws IOException {
        return s3Service.upload(f.getBytes(), f.getOriginalFilename(), f.getContentType());
    }

    // =========================================================================
    // دروستکردنی مادەکانی ئەلبوم
    // =========================================================================

    private List<ImageAlbumItem> buildAlbumItems(
            ImageCollection owner,
            ImageCollectionType type,
            List<ImageItemDto> dtos,
            List<MultipartFile> files
    ) throws IOException {

        int fileCount = (files == null) ? 0
                : (int) files.stream().filter(f -> f != null && !f.isEmpty()).count();
        int dtoCount  = (dtos  == null) ? 0 : dtos.size();
        int max       = Math.max(fileCount, dtoCount);

        validateAlbumItemCount(type, max);

        List<ImageAlbumItem> out = new ArrayList<>(max);
        int fileIndex = 0;

        for (int i = 0; i < max; i++) {
            MultipartFile file = nextNonEmpty(files, fileIndex);
            if (file != null) fileIndex = advanceFileIndex(files, fileIndex);

            ImageItemDto dto = (dtos != null && i < dtos.size()) ? dtos.get(i) : null;

            ImageAlbumItem item = new ImageAlbumItem();
            item.setImageCollection(owner);
            item.setSortOrder(dto != null && dto.getSortOrder() != null ? dto.getSortOrder() : i);

            if (dto != null) {
                item.setCaptionCkb(trimOrNull(dto.getCaptionCkb()));
                item.setCaptionKmr(trimOrNull(dto.getCaptionKmr()));
                item.setDescriptionCkb(trimOrNull(dto.getDescriptionCkb()));
                item.setDescriptionKmr(trimOrNull(dto.getDescriptionKmr()));
            }

            applyImageSource(item, file, dto);
            out.add(item);
        }
        return out;
    }

    private void applyImageSource(ImageAlbumItem item, MultipartFile file, ImageItemDto dto)
            throws IOException {

        if (hasFile(file)) {
            item.setImageUrl(uploadFile(file));
            item.setExternalUrl(null);
            item.setEmbedUrl(null);
            return;
        }

        String s3  = dto != null ? trimOrNull(dto.getImageUrl())    : null;
        String ext = dto != null ? trimOrNull(dto.getExternalUrl()) : null;
        String emb = dto != null ? trimOrNull(dto.getEmbedUrl())    : null;

        if (isBlank(s3) && isBlank(ext) && isBlank(emb)) {
            // هەڵە: دەبێت سەرچاوەیەکی وێنە دیاری بکرێت
            throw new BadRequestException("image.source.required", Map.of(
                    "message",
                    "هەر وێنەیەک پێویستی بە فایل یان لینکی ڕاستەقینە یان لینکی دەرەکی یان ئێمبێد هەیە"));
        }
        item.setImageUrl(s3);
        item.setExternalUrl(ext);
        item.setEmbedUrl(emb);
    }

    // =========================================================================
    // پشتڕاستکردنەوەی جۆری کۆمەڵە
    // =========================================================================

    /**
     * پشتڕاستکردنەوەی ژمارەی وێنەکان بەپێی جۆر:
     * - SINGLE: تەنها ١ وێنە
     * - GALLERY: لانیکەم ١ وێنە
     * - PHOTO_STORY: لانیکەم ٢ وێنە
     *
     * @throws BadRequestException - ژمارە نادروستە
     */
    private void validateAlbumItemCount(ImageCollectionType type, int count) {
        switch (type) {
            case SINGLE -> {
                if (count != 1)
                    // هەڵە: جۆری تاک پێویستی بە تەنها یەک وێنەیە
                    throw new BadRequestException("imageCollection.single.invalid",
                            Map.of("message", "جۆری SINGLE پێویستی بە تەنها ١ وێنەیە", "count", count));
            }
            case GALLERY -> {
                if (count < 1)
                    // هەڵە: گەلێری پێویستی بە لانیکەم یەک وێنەیە
                    throw new BadRequestException("imageCollection.gallery.invalid",
                            Map.of("message", "جۆری GALLERY پێویستی بە لانیکەم ١ وێنەیە"));
            }
            case PHOTO_STORY -> {
                if (count < 2)
                    // هەڵە: چیرۆکی وێنەیی پێویستی بە لانیکەم دوو وێنەیە
                    throw new BadRequestException("imageCollection.photoStory.invalid",
                            Map.of("message", "جۆری PHOTO_STORY پێویستی بە لانیکەم ٢ وێنەیە", "count", count));
            }
        }
    }

    // =========================================================================
    // یاریدەدەرەکانی ناوەڕۆک
    // =========================================================================

    private void applyContentByLanguages(
            ImageCollection entity,
            Set<Language> langs,
            LanguageContentDto ckb,
            LanguageContentDto kmr
    ) {
        Set<Language> safe = safeLangs(langs);

        entity.setCkbContent(safe.contains(Language.CKB) ? buildContent(ckb) : null);
        entity.setKmrContent(safe.contains(Language.KMR) ? buildContent(kmr) : null);
    }

    private ImageContent buildContent(LanguageContentDto dto) {
        if (dto == null) return null;
        if (isBlank(dto.getTitle()) && isBlank(dto.getDescription())
                && isBlank(dto.getTopic()) && isBlank(dto.getLocation())
                && isBlank(dto.getCollectedBy())) return null;

        return ImageContent.builder()
                .title(trimOrNull(dto.getTitle()))
                .description(trimOrNull(dto.getDescription()))
                .location(trimOrNull(dto.getLocation()))
                .collectedBy(trimOrNull(dto.getCollectedBy()))
                .build();
    }

    // =========================================================================
    // پشتڕاستکردنەوەی دروستکردن
    // =========================================================================

    /**
     * پشتڕاستکردنەوەی زانیاری پێش دروستکردن
     *
     * @throws BadRequestException - داتا بەتاڵە
     * @throws BadRequestException - جۆر بەتاڵە
     * @throws BadRequestException - زمانەکان بەتاڵە
     * @throws BadRequestException - وێنەی بەرگ بەتاڵە
     */
    private void validateCreate(CreateRequest dto, MultipartFile ckbCoverImage) {
        if (dto == null)
            // هەڵە: داواکاری بەتاڵە
            throw new BadRequestException("error.validation", Map.of("field", "data"));
        if (dto.getCollectionType() == null)
            // هەڵە: جۆری کۆمەڵە دیاری نەکراوە
            throw new BadRequestException("imageCollection.type.required",
                    Map.of("field", "collectionType"));
        if (safeLangs(dto.getContentLanguages()).isEmpty())
            // هەڵە: زمانەکان دیاری نەکراون
            throw new BadRequestException("imageCollection.languages.required",
                    Map.of("field", "contentLanguages"));

        // دەبێت لانیکەم یەک وێنەی بەرگ دیاری بکرێت لە کاتی دروستکردندا
        boolean hasCoverFile = hasFile(ckbCoverImage);
        boolean hasCoverUrl  = !isBlank(dto.getCkbCoverUrl())
                || !isBlank(dto.getKmrCoverUrl())
                || !isBlank(dto.getHoverCoverUrl());
        if (!hasCoverFile && !hasCoverUrl) {
            throw new BadRequestException("imageCollection.cover.required", Map.of(
                    "field", "ckbCoverImage | ckbCoverUrl | kmrCoverUrl | hoverCoverUrl"));
        }
    }

    // =========================================================================
    // گۆڕین بۆ Response
    // =========================================================================

    private Response toResponse(ImageCollection entity) {
        Response.ResponseBuilder b = Response.builder()
                .id(entity.getId())
                .collectionType(entity.getCollectionType())
                // سێ وێنەی بەرگ
                .ckbCoverUrl(entity.getCkbCoverUrl())
                .kmrCoverUrl(entity.getKmrCoverUrl())
                .hoverCoverUrl(entity.getHoverCoverUrl())
                // بابەت
                .topicId(entity.getTopic()      != null ? entity.getTopic().getId()      : null)
                .topicNameCkb(entity.getTopic() != null ? entity.getTopic().getNameCkb() : null)
                .topicNameKmr(entity.getTopic() != null ? entity.getTopic().getNameKmr() : null)
                // مێتا
                .publishmentDate(entity.getPublishmentDate())
                .contentLanguages(entity.getContentLanguages() != null
                        ? new LinkedHashSet<>(entity.getContentLanguages()) : new LinkedHashSet<>())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());

        // ناوەڕۆکی CKB
        if (entity.getCkbContent() != null) {
            b.ckbContent(LanguageContentDto.builder()
                    .title(entity.getCkbContent().getTitle())
                    .description(entity.getCkbContent().getDescription())
                    .location(entity.getCkbContent().getLocation())
                    .collectedBy(entity.getCkbContent().getCollectedBy())
                    .build());
        }

        // ناوەڕۆکی KMR
        if (entity.getKmrContent() != null) {
            b.kmrContent(LanguageContentDto.builder()
                    .title(entity.getKmrContent().getTitle())
                    .description(entity.getKmrContent().getDescription())
                    .location(entity.getKmrContent().getLocation())
                    .collectedBy(entity.getKmrContent().getCollectedBy())
                    .build());
        }

        // تاگ و کلیلەووشەکان
        b.tags(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(entity.getTagsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(entity.getTagsKmr())))
                .build());
        b.keywords(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(entity.getKeywordsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(entity.getKeywordsKmr())))
                .build());

        // مادەکانی ئەلبوم
        List<ImageItemDto> items = entity.getImageAlbum() == null ? List.of()
                : entity.getImageAlbum().stream()
                .sorted(Comparator.comparing(i -> i.getSortOrder() != null ? i.getSortOrder() : 0))
                .map(i -> ImageItemDto.builder()
                        .id(i.getId())
                        .imageUrl(i.getImageUrl())
                        .externalUrl(i.getExternalUrl())
                        .embedUrl(i.getEmbedUrl())
                        .captionCkb(i.getCaptionCkb())
                        .captionKmr(i.getCaptionKmr())
                        .descriptionCkb(i.getDescriptionCkb())
                        .descriptionKmr(i.getDescriptionKmr())
                        .sortOrder(i.getSortOrder())
                        .build())
                .collect(Collectors.toList());
        b.imageAlbum(items);

        return b.build();
    }

    // =========================================================================
    // یاریدەدەری تۆمارکردن
    // =========================================================================

    private void createLog(Long collectionId, String title, String action, String details) {
        try {
            imageCollectionLogRepository.save(ImageCollectionLog.builder()
                    .imageCollectionId(collectionId)
                    .collectionTitle(title)
                    .action(action)
                    .details(details)
                    .performedBy("system")
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("شکستی تۆمارکردنی لۆگی کۆمەڵەی وێنە | id={}", collectionId, e);
        }
    }

    // =========================================================================
    // یاریدەدەرە گشتییەکان
    // =========================================================================

    private String titleOf(ImageCollection c) {
        if (c == null) return "";
        if (c.getCkbContent() != null && !isBlank(c.getCkbContent().getTitle()))
            return c.getCkbContent().getTitle();
        if (c.getKmrContent() != null && !isBlank(c.getKmrContent().getTitle()))
            return c.getKmrContent().getTitle();
        return "کۆمەڵەی وێنە#" + c.getId();
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Set<Language> safeLangs(Set<Language> in) {
        return in == null ? new LinkedHashSet<>() : new LinkedHashSet<>(in);
    }

    private Set<String> safeSet(Set<String> in) {
        return in == null ? new LinkedHashSet<>() : new LinkedHashSet<>(in);
    }

    private Set<String> cleanStrings(Set<String> in) {
        if (in == null) return new LinkedHashSet<>();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : in) { String t = trimOrNull(s); if (t != null) out.add(t); }
        return out;
    }

    private MultipartFile nextNonEmpty(List<MultipartFile> files, int start) {
        if (files == null) return null;
        for (int i = start; i < files.size(); i++) {
            MultipartFile f = files.get(i);
            if (f != null && !f.isEmpty()) return f;
        }
        return null;
    }

    private int advanceFileIndex(List<MultipartFile> files, int start) {
        if (files == null) return start;
        for (int i = start; i < files.size(); i++) {
            if (files.get(i) != null && !files.get(i).isEmpty()) return i + 1;
        }
        return files.size();
    }
}