package ak.dev.khi_backend.khi_app.service.publishment.image;

import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionDTO.*;
import ak.dev.khi_backend.khi_app.enums.publishment.ImageCollectionType;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageAlbumItem;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageCollection;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageCollectionLog;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageContent;
import ak.dev.khi_backend.khi_app.repository.publishment.image.ImageCollectionLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.image.ImageCollectionRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCollectionService {

    private final ImageCollectionRepository imageCollectionRepository;
    private final ImageCollectionLogRepository imageCollectionLogRepository;
    private final S3Service s3Service;

    // ============================================================
    // CREATE (multipart optional images) - O(n)
    // ============================================================
    @Transactional
    public Response create(CreateRequest dto, MultipartFile cover, List<MultipartFile> images) {
        validateCreate(dto, cover);

        try {
            String coverUrl = resolveCoverUrl(dto.getCoverUrl(), cover);

            ImageCollection entity = ImageCollection.builder()
                    .collectionType(dto.getCollectionType())
                    .coverUrl(coverUrl)
                    .publishmentDate(dto.getPublishmentDate())
                    .contentLanguages(new LinkedHashSet<>(safeLangs(dto.getContentLanguages())))
                    .tagsCkb(new LinkedHashSet<>(safeSet(dto.getTags() != null ? dto.getTags().getCkb() : null)))
                    .tagsKmr(new LinkedHashSet<>(safeSet(dto.getTags() != null ? dto.getTags().getKmr() : null)))
                    .keywordsCkb(new LinkedHashSet<>(safeSet(dto.getKeywords() != null ? dto.getKeywords().getCkb() : null)))
                    .keywordsKmr(new LinkedHashSet<>(safeSet(dto.getKeywords() != null ? dto.getKeywords().getKmr() : null)))
                    .build();

            applyContentByLanguages(entity, dto.getContentLanguages(), dto.getCkbContent(), dto.getKmrContent());

            // Build album items from uploads + dto links
            List<ImageAlbumItem> builtItems = buildAlbumItems(entity, dto.getCollectionType(), dto.getImageAlbum(), images);
            entity.getImageAlbum().clear();
            entity.getImageAlbum().addAll(builtItems);

            ImageCollection saved = imageCollectionRepository.save(entity);
            createLog(saved.getId(), titleOf(saved), "CREATE", "ImageCollection created - Type: " + saved.getCollectionType());

            // Initialize album if LAZY
            saved.getImageAlbum().size();
            return toResponse(saved);

        } catch (IOException e) {
            throw new BadRequestException("media.upload.failed", Map.of("reason", "S3 upload failed"));
        }
    }

    // ============================================================
    // UPDATE (multipart optional images) - O(n)
    // ============================================================
    @Transactional
    public Response update(Long id, UpdateRequest dto, MultipartFile cover, List<MultipartFile> images) {
        if (id == null) throw new BadRequestException("error.validation", Map.of("field", "id"));

        ImageCollection entity = imageCollectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ImageCollection not found: " + id));

        try {
            // Update collection type if provided
            if (dto != null && dto.getCollectionType() != null) {
                entity.setCollectionType(dto.getCollectionType());
            }

            // Cover priority: uploaded > dto.coverUrl > keep old
            String newCoverUrl = resolveCoverUrl(dto != null ? dto.getCoverUrl() : null, cover);
            if (!isBlank(newCoverUrl)) entity.setCoverUrl(newCoverUrl);

            if (dto != null) {
                if (dto.getPublishmentDate() != null) entity.setPublishmentDate(dto.getPublishmentDate());

                if (dto.getContentLanguages() != null) {
                    entity.getContentLanguages().clear();
                    entity.getContentLanguages().addAll(safeLangs(dto.getContentLanguages()));
                }

                // Content blocks
                applyContentByLanguages(entity, entity.getContentLanguages(), dto.getCkbContent(), dto.getKmrContent());

                // Tags
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

                // Keywords
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

                // Replace album ONLY if dto.imageAlbum != null OR uploaded images not empty
                boolean hasUploads = images != null && images.stream().anyMatch(f -> f != null && !f.isEmpty());
                if (dto.getImageAlbum() != null || hasUploads) {
                    entity.getImageAlbum().clear();
                    List<ImageAlbumItem> rebuilt = buildAlbumItems(entity, entity.getCollectionType(), dto.getImageAlbum(), images);
                    entity.getImageAlbum().addAll(rebuilt);
                }
            }

            ImageCollection saved = imageCollectionRepository.save(entity);
            createLog(saved.getId(), titleOf(saved), "UPDATE", "ImageCollection updated - Type: " + saved.getCollectionType());

            saved.getImageAlbum().size();
            return toResponse(saved);

        } catch (IOException e) {
            throw new BadRequestException("media.upload.failed", Map.of("reason", "S3 upload failed"));
        }
    }

    // ============================================================
    // GET ALL - O(n)
    // ============================================================
    @Transactional(readOnly = true)
    public List<Response> getAll() {
        List<ImageCollection> list = imageCollectionRepository.findAll();
        return list.stream().map(c -> {
            c.getImageAlbum().size();
            return toResponse(c);
        }).toList();
    }



    // ============================================================
    // DELETE
    // ============================================================
    @Transactional
    public void delete(Long id) {
        ImageCollection entity = imageCollectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ImageCollection not found: " + id));

        createLog(entity.getId(), titleOf(entity), "DELETE", "ImageCollection deleted - Type: " + entity.getCollectionType());
        imageCollectionRepository.delete(entity);
    }

    // ============================================================
    // BUILD ALBUM ITEMS - O(n) complexity
    // ============================================================
    private List<ImageAlbumItem> buildAlbumItems(ImageCollection owner,
                                                 ImageCollectionType type,
                                                 List<ImageItemDto> dtos,
                                                 List<MultipartFile> files) throws IOException {

        List<ImageAlbumItem> out = new ArrayList<>();

        int fileCount = (files == null) ? 0 : (int) files.stream().filter(f -> f != null && !f.isEmpty()).count();
        int dtoCount = (dtos == null) ? 0 : dtos.size();
        int max = Math.max(fileCount, dtoCount);

        // Validate based on collection type
        validateAlbumItemsCount(type, max);

        int fileIndex = 0;
        for (int i = 0; i < max; i++) {
            MultipartFile file = nextNonEmpty(files, fileIndex);
            if (file != null) fileIndex = advanceIndex(files, fileIndex);

            ImageItemDto dto = (dtos != null && i < dtos.size()) ? dtos.get(i) : null;

            ImageAlbumItem item = new ImageAlbumItem();
            item.setImageCollection(owner);
            item.setSortOrder(dto != null && dto.getSortOrder() != null ? dto.getSortOrder() : i);

            if (dto != null) {
                // Caption and description
                item.setCaptionCkb(trimOrNull(dto.getCaptionCkb()));
                item.setCaptionKmr(trimOrNull(dto.getCaptionKmr()));
                item.setDescriptionCkb(trimOrNull(dto.getDescriptionCkb()));
                item.setDescriptionKmr(trimOrNull(dto.getDescriptionKmr()));
            }

            // Apply source priority: upload file > dto.imageUrl/externalUrl/embedUrl
            applyImageSource(item, file, dto);

            out.add(item);
        }

        return out;
    }

    private void applyImageSource(ImageAlbumItem item, MultipartFile file, ImageItemDto dto) throws IOException {
        if (file != null && !file.isEmpty()) {
            String s3Url = s3Service.upload(file.getBytes(), file.getOriginalFilename(), file.getContentType());
            item.setImageUrl(s3Url);
            item.setExternalUrl(null);
            item.setEmbedUrl(null);
            return;
        }

        String s3 = dto != null ? trimOrNull(dto.getImageUrl()) : null;
        String ext = dto != null ? trimOrNull(dto.getExternalUrl()) : null;
        String emb = dto != null ? trimOrNull(dto.getEmbedUrl()) : null;

        // At least one source is required
        if (isBlank(s3) && isBlank(ext) && isBlank(emb)) {
            throw new BadRequestException(
                    "image.source.required",
                    Map.of("message", "Each image must include file upload OR imageUrl OR externalUrl OR embedUrl")
            );
        }

        item.setImageUrl(s3);
        item.setExternalUrl(ext);
        item.setEmbedUrl(emb);
    }

    // ============================================================
    // TYPE-SPECIFIC VALIDATION
    // ============================================================
    private void validateAlbumItemsCount(ImageCollectionType type, int totalCount) {
        switch (type) {
            case SINGLE:
                if (totalCount != 1) {
                    throw new BadRequestException(
                            "imageCollection.single.invalid",
                            Map.of("message", "SINGLE type must have exactly 1 image", "count", totalCount)
                    );
                }
                break;

            case GALLERY:
                if (totalCount < 1) {
                    throw new BadRequestException(
                            "imageCollection.gallery.invalid",
                            Map.of("message", "GALLERY type must have at least 1 image")
                    );
                }
                break;

            case PHOTO_STORY:
                if (totalCount < 2) {
                    throw new BadRequestException(
                            "imageCollection.photoStory.invalid",
                            Map.of("message", "PHOTO_STORY type must have at least 2 images", "count", totalCount)
                    );
                }
                break;
        }
    }

    // ============================================================
    // CONTENT BY LANGUAGES
    // ============================================================
    private void applyContentByLanguages(ImageCollection entity,
                                         Set<Language> langs,
                                         LanguageContentDto ckb,
                                         LanguageContentDto kmr) {

        Set<Language> safeLangs = safeLangs(langs);

        if (safeLangs.contains(Language.CKB)) {
            entity.setCkbContent(buildContent(ckb));
        } else {
            entity.setCkbContent(null);
        }

        if (safeLangs.contains(Language.KMR)) {
            entity.setKmrContent(buildContent(kmr));
        } else {
            entity.setKmrContent(null);
        }
    }

    private ImageContent buildContent(LanguageContentDto dto) {
        if (dto == null) return null;
        if (isBlank(dto.getTitle()) && isBlank(dto.getDescription()) && isBlank(dto.getTopic())
                && isBlank(dto.getLocation()) && isBlank(dto.getCollectedBy())) return null;

        return ImageContent.builder()
                .title(trimOrNull(dto.getTitle()))
                .description(trimOrNull(dto.getDescription()))
                .topic(trimOrNull(dto.getTopic()))
                .location(trimOrNull(dto.getLocation()))
                .collectedBy(trimOrNull(dto.getCollectedBy()))
                .build();
    }

    // ============================================================
    // VALIDATION
    // ============================================================
    private void validateCreate(CreateRequest dto, MultipartFile cover) {
        if (dto == null) throw new BadRequestException("error.validation", Map.of("field", "data"));

        // Validate collection type is provided
        if (dto.getCollectionType() == null) {
            throw new BadRequestException("imageCollection.type.required", Map.of("field", "collectionType"));
        }

        if (safeLangs(dto.getContentLanguages()).isEmpty()) {
            throw new BadRequestException("imageCollection.languages.required", Map.of("field", "contentLanguages"));
        }

        // Cover is required: allow coverUrl if no file
        boolean hasCoverFile = cover != null && !cover.isEmpty();
        boolean hasCoverUrl = !isBlank(dto.getCoverUrl());
        if (!hasCoverFile && !hasCoverUrl) {
            throw new BadRequestException("imageCollection.cover.required", Map.of("field", "cover/coverUrl"));
        }
    }

    private String resolveCoverUrl(String dtoCoverUrl, MultipartFile cover) throws IOException {
        if (cover != null && !cover.isEmpty()) {
            return s3Service.upload(cover.getBytes(), cover.getOriginalFilename(), cover.getContentType());
        }
        return trimOrNull(dtoCoverUrl);
    }

    // ============================================================
    // LOGS
    // ============================================================
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
            log.warn("Failed to create ImageCollection log | id={}", collectionId, e);
        }
    }

    // ============================================================
    // MAPPERS - O(n)
    // ============================================================
    private Response toResponse(ImageCollection entity) {
        Response.ResponseBuilder b = Response.builder()
                .id(entity.getId())
                .collectionType(entity.getCollectionType())
                .coverUrl(entity.getCoverUrl())
                .publishmentDate(entity.getPublishmentDate())
                .contentLanguages(entity.getContentLanguages() != null ? new LinkedHashSet<>(entity.getContentLanguages()) : new LinkedHashSet<>())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());

        if (entity.getCkbContent() != null) {
            b.ckbContent(LanguageContentDto.builder()
                    .title(entity.getCkbContent().getTitle())
                    .description(entity.getCkbContent().getDescription())
                    .topic(entity.getCkbContent().getTopic())
                    .location(entity.getCkbContent().getLocation())
                    .collectedBy(entity.getCkbContent().getCollectedBy())
                    .build());
        }

        if (entity.getKmrContent() != null) {
            b.kmrContent(LanguageContentDto.builder()
                    .title(entity.getKmrContent().getTitle())
                    .description(entity.getKmrContent().getDescription())
                    .topic(entity.getKmrContent().getTopic())
                    .location(entity.getKmrContent().getLocation())
                    .collectedBy(entity.getKmrContent().getCollectedBy())
                    .build());
        }

        b.tags(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(entity.getTagsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(entity.getTagsKmr())))
                .build());

        b.keywords(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(entity.getKeywordsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(entity.getKeywordsKmr())))
                .build());

        // Map album items
        List<ImageItemDto> items = entity.getImageAlbum() == null ? List.of() :
                entity.getImageAlbum().stream()
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

    private String titleOf(ImageCollection c) {
        if (c == null) return "";
        if (c.getCkbContent() != null && !isBlank(c.getCkbContent().getTitle())) return c.getCkbContent().getTitle();
        if (c.getKmrContent() != null && !isBlank(c.getKmrContent().getTitle())) return c.getKmrContent().getTitle();
        return "ImageCollection#" + c.getId();
    }

    // ============================================================
    // UTILS
    // ============================================================
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private String trimOrNull(String s) { if (s == null) return null; String t = s.trim(); return t.isEmpty() ? null : t; }

    private Set<Language> safeLangs(Set<Language> in) {
        return in == null ? new LinkedHashSet<>() : new LinkedHashSet<>(in);
    }

    private Set<String> safeSet(Set<String> in) {
        return in == null ? new LinkedHashSet<>() : new LinkedHashSet<>(in);
    }

    private Set<String> cleanStrings(Set<String> in) {
        if (in == null) return new LinkedHashSet<>();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : in) {
            String t = trimOrNull(s);
            if (t != null) out.add(t);
        }
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

    private int advanceIndex(List<MultipartFile> files, int start) {
        if (files == null) return start;
        for (int i = start; i < files.size(); i++) {
            MultipartFile f = files.get(i);
            if (f != null && !f.isEmpty()) return i + 1;
        }
        return files.size();
    }
}