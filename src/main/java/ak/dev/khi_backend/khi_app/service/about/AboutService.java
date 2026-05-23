package ak.dev.khi_backend.khi_app.service.about;

import ak.dev.khi_backend.khi_app.dto.about.AboutDTOs.*;
import ak.dev.khi_backend.khi_app.enums.MediaKind;
import ak.dev.khi_backend.khi_app.model.about.About;
import ak.dev.khi_backend.khi_app.model.about.AboutContent;
import ak.dev.khi_backend.khi_app.model.about.StatItem;
import ak.dev.khi_backend.khi_app.model.media.MediaItem;
import ak.dev.khi_backend.khi_app.repository.about.AboutRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AboutService {

    private final AboutRepository aboutRepository;
    private final S3Service s3Service;
    private final TiptapHtmlProcessor tiptapHtmlProcessor;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ============================================================
    // READ
    // ============================================================

    @Transactional(readOnly = true)
    public List<AboutResponse> getAllActive() {
        return aboutRepository.findAll().stream()
                .filter(About::isActive)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lookup by either the CKB slug or the KMR slug.
     * The frontend can navigate using whichever language slug it has.
     */
    @Transactional(readOnly = true)
    public AboutResponse getBySlug(String slug) {
        About about = aboutRepository.findBySlugCkbOrSlugKmr(slug, slug)
                .orElseThrow(() ->
                        new EntityNotFoundException("About page not found: " + slug));
        return toResponse(about);
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Transactional
    public AboutResponse create(AboutRequest request) {

        validateSlugs(request, null);

        About about = new About();
        about.setSlugCkb(request.getSlugCkb().trim());
        about.setSlugKmr(blankToNull(request.getSlugKmr()));

        about.setHeroImageUrl(blankToNull(request.getHeroImageUrl()));
        about.setHeroMediaType(request.getHeroMediaType() != null
                ? request.getHeroMediaType() : MediaKind.IMAGE);
        about.setHeroThumbnailUrl(blankToNull(request.getHeroThumbnailUrl()));
        about.setMediaGallery(buildGallery(request.getMediaGallery()));

        about.setCkbContent(buildAboutContent(request.getCkbContent()));
        about.setKmrContent(buildAboutContent(request.getKmrContent()));
        about.setStats(buildStats(request.getStats()));
        about.setActive(true);

        return toResponse(aboutRepository.save(about));
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @Transactional
    public AboutResponse update(Long id, AboutRequest request) {

        About about = aboutRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("About not found: " + id));

        validateSlugs(request, id);

        about.setSlugCkb(request.getSlugCkb().trim());
        about.setSlugKmr(blankToNull(request.getSlugKmr()));

        // If the hero asset changed and the old one was an S3 URL, delete the old file
        String oldHero = about.getHeroImageUrl();
        String newHero = blankToNull(request.getHeroImageUrl());
        if (oldHero != null && !oldHero.equals(newHero)) {
            s3Service.deleteFile(oldHero);
            log.info("Deleted old hero asset from S3: {}", oldHero);
        }
        about.setHeroImageUrl(newHero);
        about.setHeroMediaType(request.getHeroMediaType() != null
                ? request.getHeroMediaType() : MediaKind.IMAGE);

        String oldThumb = about.getHeroThumbnailUrl();
        String newThumb = blankToNull(request.getHeroThumbnailUrl());
        if (oldThumb != null && !oldThumb.equals(newThumb)) {
            s3Service.deleteFile(oldThumb);
        }
        about.setHeroThumbnailUrl(newThumb);

        about.setMediaGallery(buildGallery(request.getMediaGallery()));
        about.setCkbContent(buildAboutContent(request.getCkbContent()));
        about.setKmrContent(buildAboutContent(request.getKmrContent()));
        about.setStats(buildStats(request.getStats()));

        return toResponse(aboutRepository.save(about));
    }

    // ============================================================
    // DELETE
    // ============================================================

    @Transactional
    public void delete(Long id) {
        About about = aboutRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("About not found: " + id));

        // Delete dedicated hero asset from S3
        if (about.getHeroImageUrl() != null && !about.getHeroImageUrl().isBlank()) {
            s3Service.deleteFile(about.getHeroImageUrl());
        }
        if (about.getHeroThumbnailUrl() != null && !about.getHeroThumbnailUrl().isBlank()) {
            s3Service.deleteFile(about.getHeroThumbnailUrl());
        }
        if (about.getMediaGallery() != null) {
            for (MediaItem item : about.getMediaGallery()) {
                if (item == null) continue;
                if (item.getUrl() != null && !item.getUrl().isBlank()) {
                    s3Service.deleteFile(item.getUrl());
                }
                if (item.getThumbnailUrl() != null && !item.getThumbnailUrl().isBlank()) {
                    s3Service.deleteFile(item.getThumbnailUrl());
                }
            }
        }

        // Inline Tiptap media (images, audio, video) lives inside the body HTML
        // — S3-orphan cleanup is handled separately via the shared
        // DELETE /api/v1/media endpoint when the admin chooses to.

        aboutRepository.delete(about);
        log.info("Deleted about page id={}", id);
    }

    // ============================================================
    // PRIVATE HELPERS
    // ============================================================

    private void validateSlugs(AboutRequest request, Long excludeId) {

        if (request.getSlugCkb() == null || request.getSlugCkb().isBlank()) {
            throw new IllegalArgumentException("CKB slug is required");
        }

        String ckb = request.getSlugCkb().trim();
        String kmr = blankToNull(request.getSlugKmr());

        aboutRepository.findBySlugCkb(ckb).ifPresent(existing -> {
            if (!existing.getId().equals(excludeId)) {
                throw new IllegalArgumentException("CKB slug already exists: " + ckb);
            }
        });

        if (kmr != null) {
            aboutRepository.findBySlugKmr(kmr).ifPresent(existing -> {
                if (!existing.getId().equals(excludeId)) {
                    throw new IllegalArgumentException("KMR slug already exists: " + kmr);
                }
            });

            if (ckb.equals(kmr)) {
                throw new IllegalArgumentException(
                        "CKB slug and KMR slug must be different: " + ckb);
            }
        }
    }

    private AboutContent buildAboutContent(AboutContentRequest req) {
        if (req == null) return new AboutContent();
        return AboutContent.builder()
                .title(req.getTitle())
                .subtitle(req.getSubtitle())
                .metaDescription(req.getMetaDescription())
                .body(tiptapHtmlProcessor.process(req.getBody()))
                .build();
    }

    private List<MediaItem> buildGallery(List<MediaItem> gallery) {
        if (gallery == null || gallery.isEmpty()) return new ArrayList<>();
        ArrayList<MediaItem> result = new ArrayList<>();
        int idx = 0;
        for (MediaItem item : gallery) {
            if (item == null || item.getUrl() == null || item.getUrl().isBlank()) continue;
            MediaItem normalised = MediaItem.builder()
                    .url(item.getUrl().trim())
                    .kind(item.getKind() != null ? item.getKind() : MediaKind.IMAGE)
                    .thumbnailUrl(blankToNull(item.getThumbnailUrl()))
                    .captionCkb(blankToNull(item.getCaptionCkb()))
                    .captionKmr(blankToNull(item.getCaptionKmr()))
                    .sortOrder(item.getSortOrder() != null ? item.getSortOrder() : idx)
                    .build();
            result.add(normalised);
            idx++;
        }
        result.sort(java.util.Comparator.comparingInt(
                m -> m.getSortOrder() != null ? m.getSortOrder() : Integer.MAX_VALUE));
        return result;
    }

    private List<StatItem> buildStats(List<StatItemDto> stats) {
        if (stats == null || stats.isEmpty()) return new ArrayList<>();
        return stats.stream()
                .filter(s -> s != null
                        && (notBlank(s.getValue()) || notBlank(s.getLabelCkb()) || notBlank(s.getLabelKmr())))
                .map(s -> StatItem.builder()
                        .labelCkb(s.getLabelCkb())
                        .labelKmr(s.getLabelKmr())
                        .value(s.getValue())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // ─── Response Mappers ─────────────────────────────────────────────────────

    private AboutResponse toResponse(About about) {
        return AboutResponse.builder()
                .id(about.getId())
                .slugCkb(about.getSlugCkb())
                .slugKmr(about.getSlugKmr())
                .heroImageUrl(about.getHeroImageUrl())
                .heroMediaType(about.getHeroMediaType() != null
                        ? about.getHeroMediaType() : MediaKind.IMAGE)
                .heroThumbnailUrl(about.getHeroThumbnailUrl())
                .mediaGallery(about.getMediaGallery() != null
                        ? new ArrayList<>(about.getMediaGallery())
                        : List.of())
                .ckbContent(toContentResponse(about.getCkbContent()))
                .kmrContent(toContentResponse(about.getKmrContent()))
                .active(about.isActive())
                .stats(toStatsResponse(about.getStats()))
                .createdAt(about.getCreatedAt() != null
                        ? about.getCreatedAt().format(FORMATTER) : null)
                .updatedAt(about.getUpdatedAt() != null
                        ? about.getUpdatedAt().format(FORMATTER) : null)
                .build();
    }

    private AboutContentResponse toContentResponse(AboutContent content) {
        if (content == null) return null;
        return AboutContentResponse.builder()
                .title(content.getTitle())
                .subtitle(content.getSubtitle())
                .metaDescription(content.getMetaDescription())
                .body(content.getBody())
                .build();
    }

    private List<StatItemDto> toStatsResponse(List<StatItem> stats) {
        if (stats == null || stats.isEmpty()) return List.of();
        return stats.stream()
                .filter(java.util.Objects::nonNull)
                .map(s -> StatItemDto.builder()
                        .labelCkb(s.getLabelCkb())
                        .labelKmr(s.getLabelKmr())
                        .value(s.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
