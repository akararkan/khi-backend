package ak.dev.khi_backend.khi_app.service.publishment.image;

import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionDTO.*;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.ImageCollectionType;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.exceptions.NotFoundException;
import ak.dev.khi_backend.khi_app.model.publishment.image.*;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import ak.dev.khi_backend.khi_app.repository.publishment.image.ImageCollectionLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.image.ImageCollectionRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.topic.PublishmentTopicRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCollectionService {

    private static final String TOPIC_ENTITY_TYPE = "IMAGE";

    private final ImageCollectionRepository    imageCollectionRepository;
    private final ImageCollectionLogRepository imageCollectionLogRepository;
    private final PublishmentTopicRepository   topicRepository;
    private final S3Service                    s3Service;

    // =========================================================================
    // دروستکردن
    // =========================================================================

    @CacheEvict(value = "imageCollections", allEntries = true)
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
            String ckbCoverUrl   = resolveCoverUrl(dto.getCkbCoverUrl(),   ckbCoverImage);
            String kmrCoverUrl   = resolveCoverUrl(dto.getKmrCoverUrl(),   kmrCoverImage);
            String hoverCoverUrl = resolveCoverUrl(dto.getHoverCoverUrl(), hoverCoverImage);

            PublishmentTopic topic = resolveOrCreateTopic(dto.getTopicId(), dto.getNewTopic());

            ImageCollection entity = ImageCollection.builder()
                    .collectionType(dto.getCollectionType())
                    .ckbCoverUrl(ckbCoverUrl)
                    .kmrCoverUrl(kmrCoverUrl)
                    .hoverCoverUrl(hoverCoverUrl)
                    .topic(topic)
                    .publishmentDate(dto.getPublishmentDate())
                    .contentLanguages(new LinkedHashSet<>(safeLangs(dto.getContentLanguages())))
                    .tagsCkb(new LinkedHashSet<>(safeSet(
                            dto.getTags() != null ? dto.getTags().getCkb() : null)))
                    .tagsKmr(new LinkedHashSet<>(safeSet(
                            dto.getTags() != null ? dto.getTags().getKmr() : null)))
                    .keywordsCkb(new LinkedHashSet<>(safeSet(
                            dto.getKeywords() != null ? dto.getKeywords().getCkb() : null)))
                    .keywordsKmr(new LinkedHashSet<>(safeSet(
                            dto.getKeywords() != null ? dto.getKeywords().getKmr() : null)))
                    .build();

            applyContentByLanguages(entity,
                    dto.getContentLanguages(), dto.getCkbContent(), dto.getKmrContent());

            List<ImageAlbumItem> items = buildAlbumItems(
                    entity, dto.getCollectionType(), dto.getImageAlbum(), images);
            entity.getImageAlbum().addAll(items);

            ImageCollection saved = imageCollectionRepository.save(entity);
            createLog(saved.getId(), titleOf(saved), "CREATE",
                    "کۆمەڵەی وێنە دروستکرا — جۆر=" + saved.getCollectionType()
                            + (topic != null ? " بابەتid=" + topic.getId() : ""));

            return toResponse(saved);

        } catch (IOException e) {
            throw new BadRequestException("media.upload.failed",
                    Map.of("reason", "کێشە لە ناردنی وێنە: " + e.getMessage()));
        }
    }

    // =========================================================================
    // نوێکردنەوە
    // =========================================================================

    @CacheEvict(value = "imageCollections", allEntries = true)
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
            throw new BadRequestException("error.validation",
                    Map.of("field", "id", "message", "ئایدی پێویستە"));
        }

        ImageCollection entity = imageCollectionRepository.findByIdWithGraph(id)
                .orElseThrow(() -> new NotFoundException(
                        "imageCollection.not_found", Map.of("id", id)));

        try {
            if (dto.getCollectionType() != null) {
                entity.setCollectionType(dto.getCollectionType());
            }

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

            if (dto.isClearTopic()) {
                entity.setTopic(null);
            } else if (dto.getTopicId() != null || dto.getNewTopic() != null) {
                entity.setTopic(resolveOrCreateTopic(dto.getTopicId(), dto.getNewTopic()));
            }

            if (dto.getPublishmentDate() != null) {
                entity.setPublishmentDate(dto.getPublishmentDate());
            }

            if (dto.getContentLanguages() != null) {
                entity.getContentLanguages().clear();
                entity.getContentLanguages().addAll(safeLangs(dto.getContentLanguages()));
            }

            applyContentByLanguages(entity, entity.getContentLanguages(),
                    dto.getCkbContent(), dto.getKmrContent());

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

            boolean hasUploads = images != null
                    && images.stream().anyMatch(f -> f != null && !f.isEmpty());
            if (dto.getImageAlbum() != null || hasUploads) {
                entity.getImageAlbum().clear();
                entity.getImageAlbum().addAll(
                        buildAlbumItems(entity, entity.getCollectionType(),
                                dto.getImageAlbum(), images));
            }

            ImageCollection saved = imageCollectionRepository.save(entity);
            createLog(saved.getId(), titleOf(saved), "UPDATE",
                    "کۆمەڵەی وێنە نوێکرایەوە — جۆر=" + saved.getCollectionType());

            return toResponse(saved);

        } catch (IOException e) {
            throw new BadRequestException("media.upload.failed",
                    Map.of("reason", "کێشە لە ناردنی وێنە: " + e.getMessage()));
        }
    }

    // =========================================================================
    // GET ALL — Paginated + Cached + Two-Phase @BatchSize
    //
    // Execution plan for page of 20:
    //   Q1: SELECT id FROM image_collections ORDER BY date DESC   (Phase 1)
    //   Q2: SELECT ic FROM image_collections WHERE id IN (...)    (Phase 2 bare rows)
    //   Q3-Q8: @BatchSize fires 1 IN-query per collection type    (auto)
    //   Total: 8 fast queries. Cache hit: <5ms.
    // =========================================================================

    @Cacheable(value = "imageCollections", key = "'all:p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> getAll(int page, int size) {
        Page<Long> idPage = imageCollectionRepository.findAllIds(PageRequest.of(page, size));

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }

        return new PageImpl<>(
                hydrateAndSort(idPage.getContent()).stream()
                        .map(this::toResponse).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    // =========================================================================
    // FILTER BY TYPE — Paginated + Cached
    // =========================================================================

    @Cacheable(value = "imageCollections",
            key = "'type:' + #type.name() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> getByType(ImageCollectionType type, int page, int size) {
        if (type == null) {
            throw new BadRequestException("imageCollection.type.required",
                    Map.of("field", "type"));
        }

        Page<Long> idPage = imageCollectionRepository.findIdsByType(
                type, PageRequest.of(page, size));

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }

        return new PageImpl<>(
                hydrateAndSort(idPage.getContent()).stream()
                        .map(this::toResponse).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    // =========================================================================
    // SEARCH BY TAG — Paginated + Cached
    // =========================================================================

    @Cacheable(value = "imageCollections",
            key = "'tag:' + #tag.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> searchByTag(String tag, int page, int size) {
        if (isBlank(tag)) {
            throw new BadRequestException("tag.required", Map.of("field", "tag"));
        }

        Page<Long> idPage = imageCollectionRepository.findIdsByTag(
                tag.trim(), PageRequest.of(page, size));

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }

        return new PageImpl<>(
                hydrateAndSort(idPage.getContent()).stream()
                        .map(this::toResponse).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    // =========================================================================
    // SEARCH BY KEYWORD — Paginated + Cached
    // =========================================================================

    @Cacheable(value = "imageCollections",
            key = "'kw:' + #keyword.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> searchByKeyword(String keyword, int page, int size) {
        if (isBlank(keyword)) {
            throw new BadRequestException("keyword.required", Map.of("field", "keyword"));
        }

        Page<Long> idPage = imageCollectionRepository.findIdsByKeyword(
                keyword.trim(), PageRequest.of(page, size));

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }

        return new PageImpl<>(
                hydrateAndSort(idPage.getContent()).stream()
                        .map(this::toResponse).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    // =========================================================================
    // GLOBAL SEARCH — Paginated + Cached
    // =========================================================================

    @Cacheable(value = "imageCollections",
            key = "'search:' + #q.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> globalSearch(String q, int page, int size) {
        if (isBlank(q)) {
            throw new BadRequestException("keyword.required", Map.of("field", "q"));
        }

        Page<Long> idPage = imageCollectionRepository.findIdsByGlobalSearch(
                q.trim(), PageRequest.of(page, size));

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }

        return new PageImpl<>(
                hydrateAndSort(idPage.getContent()).stream()
                        .map(this::toResponse).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    // =========================================================================
    // SEARCH BY TOPIC — Paginated + Cached
    // =========================================================================

    @Cacheable(value = "imageCollections",
            key = "'topic:' + #topicId + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> getByTopic(Long topicId, int page, int size) {
        if (topicId == null) {
            throw new BadRequestException("error.validation", Map.of("field", "topicId"));
        }

        Page<Long> idPage = imageCollectionRepository.findIdsByTopic(
                topicId, PageRequest.of(page, size));

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }

        return new PageImpl<>(
                hydrateAndSort(idPage.getContent()).stream()
                        .map(this::toResponse).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    // =========================================================================
    // GET BY ID
    // =========================================================================

    @Transactional(readOnly = true)
    public Response getById(Long id) {
        ImageCollection entity = imageCollectionRepository.findByIdWithGraph(id)
                .orElseThrow(() -> new NotFoundException(
                        "imageCollection.not_found", Map.of("id", id)));
        return toResponse(entity);
    }

    // =========================================================================
    // سڕینەوە
    // =========================================================================

    @CacheEvict(value = "imageCollections", allEntries = true)
    @Transactional
    public void delete(Long id) {
        ImageCollection entity = imageCollectionRepository.findByIdWithGraph(id)
                .orElseThrow(() -> new NotFoundException(
                        "imageCollection.not_found", Map.of("id", id)));
        createLog(entity.getId(), titleOf(entity), "DELETE",
                "کۆمەڵەی وێنە سڕایەوە — جۆر=" + entity.getCollectionType());
        imageCollectionRepository.delete(entity);
    }

    // =========================================================================
    // CORE HYDRATION HELPER
    //
    // Step 1: bare rows  → 1 query
    // Step 2: @BatchSize fires automatically per collection type
    // Step 3: re-order to match Phase-1 pagination order
    // =========================================================================

    private List<ImageCollection> hydrateAndSort(List<Long> ids) {
        List<ImageCollection> rows = imageCollectionRepository.findAllByIds(ids);

        Map<Long, ImageCollection> indexed = new LinkedHashMap<>(rows.size());
        for (ImageCollection ic : rows) indexed.put(ic.getId(), ic);

        List<ImageCollection> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            ImageCollection ic = indexed.get(id);
            if (ic != null) ordered.add(ic);
        }
        return ordered;
    }

    // =========================================================================
    // یاریدەدەرەکانی بابەت
    // =========================================================================

    private PublishmentTopic resolveOrCreateTopic(Long topicId, InlineTopicRequest newTopic) {
        if (topicId != null) {
            return findImageTopicOrThrow(topicId);
        }
        if (newTopic != null) {
            if (isBlank(newTopic.getNameCkb()) && isBlank(newTopic.getNameKmr())) {
                throw new BadRequestException("error.validation", Map.of(
                        "message",
                        "بابەتی نوێ پێویستی بە لانیکەم ناوێکی کوردییە (ناوەندی یان باکوور)"));
            }
            PublishmentTopic created = topicRepository.save(
                    PublishmentTopic.builder()
                            .entityType(TOPIC_ENTITY_TYPE)
                            .nameCkb(trimOrNull(newTopic.getNameCkb()))
                            .nameKmr(trimOrNull(newTopic.getNameKmr()))
                            .build());
            log.info("بابەتی IMAGE دروستکرا inline id={}", created.getId());
            return created;
        }
        return null;
    }

    private PublishmentTopic findImageTopicOrThrow(Long topicId) {
        PublishmentTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new NotFoundException(
                        "topic.not_found", Map.of("id", topicId)));
        if (!TOPIC_ENTITY_TYPE.equals(topic.getEntityType())) {
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

    private String resolveCoverUrl(String dtoUrl, MultipartFile file) throws IOException {
        if (hasFile(file)) return uploadFile(file);
        return isBlank(dtoUrl) ? null : dtoUrl.trim();
    }

    private boolean hasFile(MultipartFile f) { return f != null && !f.isEmpty(); }

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

        int fileCount = files == null ? 0
                : (int) files.stream().filter(f -> f != null && !f.isEmpty()).count();
        int dtoCount  = dtos == null ? 0 : dtos.size();
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

    private void validateAlbumItemCount(ImageCollectionType type, int count) {
        switch (type) {
            case SINGLE -> {
                if (count != 1)
                    throw new BadRequestException("imageCollection.single.invalid",
                            Map.of("message", "جۆری SINGLE پێویستی بە تەنها ١ وێنەیە",
                                    "count", count));
            }
            case GALLERY -> {
                if (count < 1)
                    throw new BadRequestException("imageCollection.gallery.invalid",
                            Map.of("message", "جۆری GALLERY پێویستی بە لانیکەم ١ وێنەیە"));
            }
            case PHOTO_STORY -> {
                if (count < 2)
                    throw new BadRequestException("imageCollection.photoStory.invalid",
                            Map.of("message", "جۆری PHOTO_STORY پێویستی بە لانیکەم ٢ وێنەیە",
                                    "count", count));
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
                && isBlank(dto.getLocation()) && isBlank(dto.getCollectedBy())) return null;
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

    private void validateCreate(CreateRequest dto, MultipartFile ckbCoverImage) {
        if (dto == null)
            throw new BadRequestException("error.validation", Map.of("field", "data"));
        if (dto.getCollectionType() == null)
            throw new BadRequestException("imageCollection.type.required",
                    Map.of("field", "collectionType"));
        if (safeLangs(dto.getContentLanguages()).isEmpty())
            throw new BadRequestException("imageCollection.languages.required",
                    Map.of("field", "contentLanguages"));

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
    //
    // CRITICAL: every collection access happens inside open @Transactional.
    // new LinkedHashSet<>(...) copies into plain Java type — prevents
    // LazyInitializationException after session closes.
    // =========================================================================

    private Response toResponse(ImageCollection entity) {
        Response.ResponseBuilder b = Response.builder()
                .id(entity.getId())
                .collectionType(entity.getCollectionType())
                .ckbCoverUrl(entity.getCkbCoverUrl())
                .kmrCoverUrl(entity.getKmrCoverUrl())
                .hoverCoverUrl(entity.getHoverCoverUrl())
                .topicId(entity.getTopic()      != null ? entity.getTopic().getId()      : null)
                .topicNameCkb(entity.getTopic() != null ? entity.getTopic().getNameCkb() : null)
                .topicNameKmr(entity.getTopic() != null ? entity.getTopic().getNameKmr() : null)
                .publishmentDate(entity.getPublishmentDate())
                // ← copy into plain Set — prevents LazyInitializationException
                .contentLanguages(entity.getContentLanguages() != null
                        ? new LinkedHashSet<>(entity.getContentLanguages())
                        : new LinkedHashSet<>())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());

        if (entity.getCkbContent() != null) {
            b.ckbContent(LanguageContentDto.builder()
                    .title(entity.getCkbContent().getTitle())
                    .description(entity.getCkbContent().getDescription())
                    .location(entity.getCkbContent().getLocation())
                    .collectedBy(entity.getCkbContent().getCollectedBy())
                    .build());
        }

        if (entity.getKmrContent() != null) {
            b.kmrContent(LanguageContentDto.builder()
                    .title(entity.getKmrContent().getTitle())
                    .description(entity.getKmrContent().getDescription())
                    .location(entity.getKmrContent().getLocation())
                    .collectedBy(entity.getKmrContent().getCollectedBy())
                    .build());
        }

        // ← copy into plain Set
        b.tags(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(entity.getTagsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(entity.getTagsKmr())))
                .build());
        b.keywords(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(entity.getKeywordsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(entity.getKeywordsKmr())))
                .build());

        // ← copy into plain List
        List<ImageItemDto> items = entity.getImageAlbum() == null ? List.of()
                : new ArrayList<>(entity.getImageAlbum()).stream()
                .sorted(Comparator.comparing(
                        i -> i.getSortOrder() != null ? i.getSortOrder() : 0))
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
    // تۆمارکردنی چالاکی
    // =========================================================================

    private void createLog(Long id, String title, String action, String details) {
        try {
            imageCollectionLogRepository.save(ImageCollectionLog.builder()
                    .imageCollectionId(id)
                    .collectionTitle(title)
                    .action(action)
                    .details(details)
                    .performedBy("system")
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("شکستی تۆمارکردنی لۆگی کۆمەڵەی وێنە | id={}", id, e);
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

    private boolean isBlank(String s)   { return s == null || s.isBlank(); }
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
            if (files.get(i) != null && !files.get(i).isEmpty()) return files.get(i);
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