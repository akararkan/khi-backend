package ak.dev.khi_backend.khi_app.service.about;

import ak.dev.khi_backend.khi_app.dto.about.AboutDTOs.*;
import ak.dev.khi_backend.khi_app.model.about.About;
import ak.dev.khi_backend.khi_app.model.about.AboutContent;
import ak.dev.khi_backend.khi_app.model.about.StatItem;
import ak.dev.khi_backend.khi_app.repository.about.AboutRepository;
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
