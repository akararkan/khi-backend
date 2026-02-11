package ak.dev.khi_backend.khi_app.dto.publishment.image;


import ak.dev.khi_backend.khi_app.model.publishment.image.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ImageCollectionMapper {

    private ImageCollectionMapper() {}

    // ─── DTO → Entity (coverUrl set by service after S3 upload) ───────
    public static ImageCollection toEntity(ImageCollectionDTO dto) {
        return ImageCollection.builder()
                .ckbContent(toContentEntity(dto.getCkbContent()))
                .kmrContent(toContentEntity(dto.getKmrContent()))
                .publishmentDate(dto.getPublishmentDate())
                .contentLanguages(safeSet(dto.getContentLanguages()))
                .tagsCkb(safeSet(dto.getTagsCkb()))
                .tagsKmr(safeSet(dto.getTagsKmr()))
                .keywordsCkb(safeSet(dto.getKeywordsCkb()))
                .keywordsKmr(safeSet(dto.getKeywordsKmr()))
                .build();
    }

    // ─── Entity → DTO ─────────────────────────────────────────────────
    public static ImageCollectionDTO toDTO(ImageCollection entity) {
        return ImageCollectionDTO.builder()
                .id(entity.getId())
                .coverUrl(entity.getCoverUrl())
                .ckbContent(toContentDTO(entity.getCkbContent()))
                .kmrContent(toContentDTO(entity.getKmrContent()))
                .imageAlbum(toAlbumItemDTOList(entity.getImageAlbum()))
                .publishmentDate(entity.getPublishmentDate())
                .contentLanguages(entity.getContentLanguages())
                .tagsCkb(entity.getTagsCkb())
                .tagsKmr(entity.getTagsKmr())
                .keywordsCkb(entity.getKeywordsCkb())
                .keywordsKmr(entity.getKeywordsKmr())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // ─── Update entity from DTO (preserves existing cover & images if not re-uploaded) ──
    public static void updateEntity(ImageCollection entity, ImageCollectionDTO dto) {
        entity.setCkbContent(toContentEntity(dto.getCkbContent()));
        entity.setKmrContent(toContentEntity(dto.getKmrContent()));
        entity.setPublishmentDate(dto.getPublishmentDate());

        updateSet(entity.getContentLanguages(), dto.getContentLanguages());
        updateSet(entity.getTagsCkb(), dto.getTagsCkb());
        updateSet(entity.getTagsKmr(), dto.getTagsKmr());
        updateSet(entity.getKeywordsCkb(), dto.getKeywordsCkb());
        updateSet(entity.getKeywordsKmr(), dto.getKeywordsKmr());
    }

    // ─── Log → DTO ───────────────────────────────────────────────────
    public static ImageCollectionLogDTO toLogDTO(ImageCollectionLog log) {
        return ImageCollectionLogDTO.builder()
                .id(log.getId())
                .imageCollectionId(log.getImageCollectionId())
                .collectionTitle(log.getCollectionTitle())
                .action(log.getAction())
                .details(log.getDetails())
                .performedBy(log.getPerformedBy())
                .timestamp(log.getTimestamp())
                .build();
    }

    // ─── Album Item helpers ───────────────────────────────────────────

    public static ImageAlbumItemDTO toAlbumItemDTO(ImageAlbumItem item) {
        return ImageAlbumItemDTO.builder()
                .id(item.getId())
                .imageUrl(item.getImageUrl())
                .descriptionCkb(item.getDescriptionCkb())
                .descriptionKmr(item.getDescriptionKmr())
                .sortOrder(item.getSortOrder())
                .build();
    }

    public static List<ImageAlbumItemDTO> toAlbumItemDTOList(List<ImageAlbumItem> items) {
        if (items == null) return List.of();
        return items.stream()
                .map(ImageCollectionMapper::toAlbumItemDTO)
                .collect(Collectors.toList());
    }

    // ─── Content helpers ──────────────────────────────────────────────

    private static ImageContent toContentEntity(ImageContentDTO dto) {
        if (dto == null) return null;
        return ImageContent.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .topic(dto.getTopic())
                .location(dto.getLocation())
                .collectedBy(dto.getCollectedBy())
                .build();
    }

    private static ImageContentDTO toContentDTO(ImageContent content) {
        if (content == null) return null;
        return ImageContentDTO.builder()
                .title(content.getTitle())
                .description(content.getDescription())
                .topic(content.getTopic())
                .location(content.getLocation())
                .collectedBy(content.getCollectedBy())
                .build();
    }

    private static <T> LinkedHashSet<T> safeSet(Set<T> set) {
        return set != null ? new LinkedHashSet<>(set) : new LinkedHashSet<>();
    }

    private static <T> void updateSet(Set<T> target, Set<T> source) {
        if (source != null) {
            target.clear();
            target.addAll(source);
        }
    }
}