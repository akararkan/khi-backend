package ak.dev.khi_backend.khi_app.service.about;

import ak.dev.khi_backend.khi_app.dto.about.AboutDTOs.*;
import ak.dev.khi_backend.khi_app.enums.project.ProjectMediaType;
import ak.dev.khi_backend.khi_app.model.about.About;
import ak.dev.khi_backend.khi_app.model.about.AboutBlock;
import ak.dev.khi_backend.khi_app.model.about.AboutBlockContent;
import ak.dev.khi_backend.khi_app.model.about.AboutContent;
import ak.dev.khi_backend.khi_app.repository.about.AboutRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AboutService {

    private final AboutRepository aboutRepository;
    private final S3Service s3Service;

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
        about.setSlugKmr(request.getSlugKmr() != null && !request.getSlugKmr().isBlank()
                ? request.getSlugKmr().trim() : null);

        about.setHeroImageUrl(
                request.getHeroImageUrl() != null && !request.getHeroImageUrl().isBlank()
                        ? request.getHeroImageUrl().trim() : null);

        about.setCkbContent(buildAboutContent(request.getCkbContent()));
        about.setKmrContent(buildAboutContent(request.getKmrContent()));
        about.setActive(true);

        if (request.getBlocks() != null) {
            int sequence = 0;
            for (AboutBlockRequest blockReq : request.getBlocks()) {
                about.addBlock(buildBlock(blockReq, sequence++));
            }
        }

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
        about.setSlugKmr(request.getSlugKmr() != null && !request.getSlugKmr().isBlank()
                ? request.getSlugKmr().trim() : null);

        // If the hero image changed and the old one was an S3 URL, delete the old file
        String oldHero = about.getHeroImageUrl();
        String newHero = request.getHeroImageUrl() != null && !request.getHeroImageUrl().isBlank()
                ? request.getHeroImageUrl().trim() : null;
        if (oldHero != null && !oldHero.equals(newHero)) {
            s3Service.deleteFile(oldHero);
            log.info("Deleted old hero image from S3: {}", oldHero);
        }
        about.setHeroImageUrl(newHero);

        about.setCkbContent(buildAboutContent(request.getCkbContent()));
        about.setKmrContent(buildAboutContent(request.getKmrContent()));

        // orphanRemoval = true handles DB cleanup
        about.getBlocks().clear();

        if (request.getBlocks() != null) {
            int sequence = 0;
            for (AboutBlockRequest blockReq : request.getBlocks()) {
                about.addBlock(buildBlock(blockReq, sequence++));
            }
        }

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

        // Delete dedicated hero image from S3
        if (about.getHeroImageUrl() != null && !about.getHeroImageUrl().isBlank()) {
            s3Service.deleteFile(about.getHeroImageUrl());
        }

        // Delete all block media from S3
        about.getBlocks().stream()
                .filter(b -> b.getMediaUrl() != null && !b.getMediaUrl().isBlank())
                .forEach(b -> s3Service.deleteFile(b.getMediaUrl()));

        aboutRepository.delete(about);
        log.info("Deleted about page id={}", id);
    }

    // ============================================================
    // MEDIA UPLOAD — S3
    // ============================================================

    public UploadResponse uploadMedia(MultipartFile file, String type) throws IOException {

        log.info("Uploading about media: name={}, hint={}, contentType={}, size={}",
                file.getOriginalFilename(), type,
                file.getContentType(), file.getSize());

        ProjectMediaType mediaType = resolveMediaType(type);

        String fileUrl = mediaType != null
                ? s3Service.upload(file.getBytes(), file.getOriginalFilename(),
                file.getContentType(), mediaType)
                : s3Service.upload(file.getBytes(), file.getOriginalFilename(),
                file.getContentType());

        log.info("Upload successful: {}", fileUrl);

        return UploadResponse.builder()
                .fileUrl(fileUrl)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .build();
    }

    public UploadResponse uploadMedia(MultipartFile file) throws IOException {
        return uploadMedia(file, null);
    }

    public List<UploadResponse> uploadMultipleMedia(List<MultipartFile> files, String type) {
        return files.stream()
                .map(file -> {
                    try {
                        return uploadMedia(file, type);
                    } catch (IOException e) {
                        log.error("Failed to upload: {}", file.getOriginalFilename(), e);
                        throw new RuntimeException("Upload failed: " + file.getOriginalFilename(), e);
                    }
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteMedia(String fileUrl) {
        if (fileUrl != null && !fileUrl.isBlank()) {
            s3Service.deleteFile(fileUrl);
            log.info("Deleted media from S3: {}", fileUrl);
        }
    }

    // ============================================================
    // PRIVATE HELPERS
    // ============================================================

    /**
     * Validate slug uniqueness for both CKB and KMR.
     * On update, excludes the current record's own ID from the uniqueness check.
     */
    private void validateSlugs(AboutRequest request, Long excludeId) {

        if (request.getSlugCkb() == null || request.getSlugCkb().isBlank()) {
            throw new IllegalArgumentException("CKB slug is required");
        }

        String ckb = request.getSlugCkb().trim();
        String kmr = (request.getSlugKmr() != null && !request.getSlugKmr().isBlank())
                ? request.getSlugKmr().trim() : null;

        // CKB slug uniqueness
        aboutRepository.findBySlugCkb(ckb).ifPresent(existing -> {
            if (!existing.getId().equals(excludeId)) {
                throw new IllegalArgumentException("CKB slug already exists: " + ckb);
            }
        });

        // KMR slug uniqueness (only if provided)
        if (kmr != null) {
            aboutRepository.findBySlugKmr(kmr).ifPresent(existing -> {
                if (!existing.getId().equals(excludeId)) {
                    throw new IllegalArgumentException("KMR slug already exists: " + kmr);
                }
            });

            // CKB and KMR slugs must not be identical
            if (ckb.equals(kmr)) {
                throw new IllegalArgumentException(
                        "CKB slug and KMR slug must be different: " + ckb);
            }
        }
    }

    private ProjectMediaType resolveMediaType(String hint) {
        if (hint == null) return null;
        return switch (hint.toLowerCase().trim()) {
            case "image"   -> ProjectMediaType.IMAGE;
            case "video"   -> ProjectMediaType.VIDEO;
            case "audio"   -> ProjectMediaType.AUDIO;
            case "gallery" -> ProjectMediaType.IMAGE;
            default        -> null;
        };
    }

    private AboutContent buildAboutContent(AboutContentRequest req) {
        if (req == null) return new AboutContent();
        return AboutContent.builder()
                .title(req.getTitle())
                .subtitle(req.getSubtitle())
                .metaDescription(req.getMetaDescription())
                .build();
    }

    private AboutBlockContent buildBlockContent(AboutBlockContentRequest req) {
        if (req == null) return new AboutBlockContent();
        return AboutBlockContent.builder()
                .contentText(req.getContentText())
                .title(req.getTitle())
                .altText(req.getAltText())
                .build();
    }

    private AboutBlock buildBlock(AboutBlockRequest req, int sequence) {

        AboutBlock.ContentType contentType;
        try {
            contentType = AboutBlock.ContentType.valueOf(req.getContentType().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid content type: " + req.getContentType());
        }

        AboutBlock.AboutBlockBuilder builder = AboutBlock.builder()
                .contentType(contentType)
                .sequence(sequence)
                .ckbContent(buildBlockContent(req.getCkbContent()))
                .kmrContent(buildBlockContent(req.getKmrContent()))
                .mediaUrl(req.getMediaUrl())
                .thumbnailUrl(req.getThumbnailUrl());

        if (req.getMetadata() != null) {
            builder.metadata((java.util.Map<String, Object>) req.getMetadata());
        }

        return builder.build();
    }

    // ─── Response Mappers ─────────────────────────────────────────────────────

    private AboutResponse toResponse(About about) {

        List<AboutBlockResponse> blockResponses = about.getBlocks().stream()
                .sorted(Comparator.comparingInt(AboutBlock::getSequence))
                .map(this::toBlockResponse)
                .collect(Collectors.toList());

        return AboutResponse.builder()
                .id(about.getId())
                .slugCkb(about.getSlugCkb())
                .slugKmr(about.getSlugKmr())
                .heroImageUrl(about.getHeroImageUrl())
                .ckbContent(toAboutContentResponse(about.getCkbContent()))
                .kmrContent(toAboutContentResponse(about.getKmrContent()))
                .active(about.isActive())
                .blocks(blockResponses)
                .createdAt(about.getCreatedAt() != null
                        ? about.getCreatedAt().format(FORMATTER) : null)
                .updatedAt(about.getUpdatedAt() != null
                        ? about.getUpdatedAt().format(FORMATTER) : null)
                .build();
    }

    private AboutContentResponse toAboutContentResponse(AboutContent content) {
        if (content == null) return null;
        return AboutContentResponse.builder()
                .title(content.getTitle())
                .subtitle(content.getSubtitle())
                .metaDescription(content.getMetaDescription())
                .build();
    }

    private AboutBlockResponse toBlockResponse(AboutBlock block) {
        return AboutBlockResponse.builder()
                .id(block.getId())
                .contentType(block.getContentType().name())
                .sequence(block.getSequence())
                .ckbContent(toBlockContentResponse(block.getCkbContent()))
                .kmrContent(toBlockContentResponse(block.getKmrContent()))
                .mediaUrl(block.getMediaUrl())
                .thumbnailUrl(block.getThumbnailUrl())
                .metadata(block.getMetadata())
                .build();
    }

    private AboutBlockContentResponse toBlockContentResponse(AboutBlockContent content) {
        if (content == null) return null;
        return AboutBlockContentResponse.builder()
                .contentText(content.getContentText())
                .title(content.getTitle())
                .altText(content.getAltText())
                .build();
    }
}