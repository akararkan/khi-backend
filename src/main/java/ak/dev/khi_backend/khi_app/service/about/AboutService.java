package ak.dev.khi_backend.khi_app.service.about;

import ak.dev.khi_backend.khi_app.dto.about.AboutDTOs.*;
import ak.dev.khi_backend.khi_app.enums.project.ProjectMediaType;
import ak.dev.khi_backend.khi_app.model.about.About;
import ak.dev.khi_backend.khi_app.model.about.AboutBlock;
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

    @Transactional(readOnly = true)
    public AboutResponse getBySlug(String slug) {
        About about = aboutRepository.findBySlugWithBlocks(slug)
                .orElseThrow(() ->
                        new EntityNotFoundException("About page not found: " + slug));
        return toResponse(about);
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Transactional
    public AboutResponse create(AboutRequest request) {

        if (aboutRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException(
                    "Slug already exists: " + request.getSlug());
        }

        About about = new About();
        about.setSlug(request.getSlug());
        about.setTitle(request.getTitle());
        about.setSubtitle(request.getSubtitle());
        about.setMetaDescription(request.getMetaDescription());
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

        about.setSlug(request.getSlug());
        about.setTitle(request.getTitle());
        about.setSubtitle(request.getSubtitle());
        about.setMetaDescription(request.getMetaDescription());

        // orphanRemoval = true on the blocks collection handles DB cleanup
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

        // Delete all S3 media attached to blocks before removing the entity
        about.getBlocks().stream()
                .filter(b -> b.getMediaUrl() != null && !b.getMediaUrl().isBlank())
                .forEach(b -> s3Service.deleteFile(b.getMediaUrl()));

        aboutRepository.delete(about);
        log.info("Deleted about page id={}", id);
    }

    // ============================================================
    // MEDIA UPLOAD — S3
    // ============================================================

    /**
     * Upload a single file.
     * The {@code type} hint ("image", "video", "audio") is used to pick
     * the right S3 folder via {@link ProjectMediaType}.  When null or
     * unrecognised, S3Service falls back to content-type auto-detection.
     */
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

    /**
     * Convenience overload — type hint is derived from the file's content-type.
     */
    public UploadResponse uploadMedia(MultipartFile file) throws IOException {
        return uploadMedia(file, null);
    }

    /**
     * Bulk upload — all files share the same type hint.
     */
    public List<UploadResponse> uploadMultipleMedia(List<MultipartFile> files,
                                                    String type) {
        return files.stream()
                .map(file -> {
                    try {
                        return uploadMedia(file, type);
                    } catch (IOException e) {
                        log.error("Failed to upload: {}", file.getOriginalFilename(), e);
                        throw new RuntimeException(
                                "Upload failed: " + file.getOriginalFilename(), e);
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Delete a single S3 file by URL.
     * Safe to call — S3Service swallows deletion errors internally.
     */
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
     * Map the free-text type hint from the controller to a {@link ProjectMediaType}.
     * Returns {@code null} when no match so S3Service auto-detects from content-type.
     */
    private ProjectMediaType resolveMediaType(String hint) {
        if (hint == null) return null;
        return switch (hint.toLowerCase().trim()) {
            case "image"   -> ProjectMediaType.IMAGE;
            case "video"   -> ProjectMediaType.VIDEO;
            case "audio"   -> ProjectMediaType.AUDIO;
            case "gallery" -> ProjectMediaType.IMAGE;   // gallery items are images
            default        -> null;
        };
    }

    private AboutBlock buildBlock(AboutBlockRequest req, int sequence) {

        AboutBlock.ContentType contentType;
        try {
            contentType = AboutBlock.ContentType.valueOf(
                    req.getContentType().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid content type: " + req.getContentType());
        }

        AboutBlock.AboutBlockBuilder builder = AboutBlock.builder()
                .contentType(contentType)
                .sequence(sequence)
                .contentText(req.getContentText())
                .title(req.getTitle())
                .altText(req.getAltText())
                .mediaUrl(req.getMediaUrl());

        if (req.getMetadata() != null) {
            builder.metadata((java.util.Map<String, Object>) req.getMetadata());
        }

        return builder.build();
    }

    private AboutResponse toResponse(About about) {

        List<AboutBlockResponse> blockResponses = about.getBlocks().stream()
                .sorted(Comparator.comparingInt(AboutBlock::getSequence))
                .map(this::toBlockResponse)
                .collect(Collectors.toList());

        return AboutResponse.builder()
                .id(about.getId())
                .slug(about.getSlug())
                .title(about.getTitle())
                .subtitle(about.getSubtitle())
                .metaDescription(about.getMetaDescription())
                .active(about.isActive())
                .blocks(blockResponses)
                .createdAt(about.getCreatedAt() != null
                        ? about.getCreatedAt().format(FORMATTER) : null)
                .updatedAt(about.getUpdatedAt() != null
                        ? about.getUpdatedAt().format(FORMATTER) : null)
                .build();
    }

    private AboutBlockResponse toBlockResponse(AboutBlock block) {
        return AboutBlockResponse.builder()
                .id(block.getId())
                .contentType(block.getContentType().name())
                .sequence(block.getSequence())
                .contentText(block.getContentText())
                .mediaUrl(block.getMediaUrl())
                .thumbnailUrl(block.getThumbnailUrl())
                .title(block.getTitle())
                .altText(block.getAltText())
                .metadata(block.getMetadata())
                .build();
    }
}