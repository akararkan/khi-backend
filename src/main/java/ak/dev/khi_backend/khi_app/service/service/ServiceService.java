package ak.dev.khi_backend.khi_app.service.service;

import ak.dev.khi_backend.khi_app.dto.service.ServiceDTOs.*;
import ak.dev.khi_backend.khi_app.enums.project.ProjectMediaType;
import ak.dev.khi_backend.khi_app.model.service.*;
import ak.dev.khi_backend.khi_app.repository.service.*;
import ak.dev.khi_backend.khi_app.service.MediaMetadataExtractor;
import ak.dev.khi_backend.khi_app.service.MediaMetadataExtractor.MediaFileMeta;
import ak.dev.khi_backend.khi_app.service.S3Service;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ServiceService — Business logic for the Service module.
 *
 * ─── Responsibilities ─────────────────────────────────────────────────────────
 *  • CRUD for {@link ak.dev.khi_backend.khi_app.model.service.Service} entities.
 *  • Bilingual content management (CKB / KMR rows in service_contents).
 *  • Media collection + file management with full S3 lifecycle.
 *  • Automatic technical metadata extraction via {@link MediaMetadataExtractor}
 *    — clients never need to send fileSize, format, resolution, duration,
 *    codec, or bitrate.  The system detects these from the uploaded bytes.
 *
 * ─── Metadata Extraction Flow ─────────────────────────────────────────────────
 *  1. Admin calls POST /api/v1/services/upload  (multipart)
 *  2. {@link #uploadMedia} reads the raw bytes, calls
 *     {@link MediaMetadataExtractor#extract} BEFORE uploading to S3.
 *  3. The enriched {@link UploadResponse} is returned — it contains the S3 URL
 *     plus all detected technical fields (format, resolution, duration …).
 *  4. Admin creates the service, referencing file URLs from step 3.
 *     The technical metadata stored in {@link ServiceMediaFile} is carried over
 *     from the upload cache embedded in the {@link UploadResponse} returned to
 *     the admin's browser — it is auto-populated in the form, not typed.
 *
 * ─── Note on FileAddRequest Metadata ─────────────────────────────────────────
 *  When a file is added via the standalone POST /collections/{id}/files endpoint,
 *  the service downloads the file from S3 and re-extracts metadata to ensure
 *  the DB is always accurate regardless of how the file was attached.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceService {

    private static final Set<String>    ALLOWED_LANG_CODES = Set.of("CKB", "KMR");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ServiceRepository                serviceRepository;
    private final ServiceContentRepository         contentRepository;
    private final ServiceMediaCollectionRepository collectionRepository;
    private final ServiceMediaFileRepository       fileRepository;
    private final S3Service                        s3Service;
    private final MediaMetadataExtractor           metadataExtractor;

    // =========================================================================
    // READ
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ServiceResponse> getAllActive() {
        return serviceRepository
                .findByActiveTrueOrderByPublishedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> getAllActiveByType(String serviceType) {
        return serviceRepository
                .findByActiveTrueAndServiceTypeIgnoreCaseOrderByPublishedAtDesc(serviceType)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServiceResponse getById(Long id) {
        ak.dev.khi_backend.khi_app.model.service.Service service =
                serviceRepository.findByIdWithAll(id)
                        .orElseThrow(() -> new EntityNotFoundException("Service not found: " + id));
        return toResponse(service);
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    @Transactional
    public ServiceResponse create(ServiceRequest request) {

        validateContents(request.getContents());

        ak.dev.khi_backend.khi_app.model.service.Service service =
                ak.dev.khi_backend.khi_app.model.service.Service.builder()
                        .serviceType(trimRequired(request.getServiceType(), "serviceType"))
                        .location(trimOrNull(request.getLocation()))
                        .coverMediaUrl(trimOrNull(request.getCoverMediaUrl()))
                        .active(true)
                        .publishedAt(parseDateTime(request.getPublishedAt()))
                        .build();

        if (request.getContents() != null) {
            for (ServiceContentRequest cr : request.getContents()) {
                service.addContent(buildContent(cr));
            }
        }

        if (request.getMediaCollections() != null) {
            int seq = 0;
            for (ServiceMediaCollectionRequest cr : request.getMediaCollections()) {
                service.addMediaCollection(buildCollection(cr, seq++));
            }
        }

        return toResponse(serviceRepository.save(service));
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    @Transactional
    public ServiceResponse update(Long id, ServiceRequest request) {

        ak.dev.khi_backend.khi_app.model.service.Service service =
                serviceRepository.findByIdWithAll(id)
                        .orElseThrow(() -> new EntityNotFoundException("Service not found: " + id));

        validateContents(request.getContents());

        service.setServiceType(trimRequired(request.getServiceType(), "serviceType"));
        service.setLocation(trimOrNull(request.getLocation()));

        // If cover media changed — delete the old S3 file
        String oldCover = service.getCoverMediaUrl();
        String newCover = trimOrNull(request.getCoverMediaUrl());
        if (oldCover != null && !oldCover.equals(newCover)) {
            s3Service.deleteFile(oldCover);
            log.info("Deleted old cover media from S3: {}", oldCover);
        }
        service.setCoverMediaUrl(newCover);
        service.setPublishedAt(parseDateTime(request.getPublishedAt()));

        // ── Replace bilingual content rows ──────────────────────────────────
        service.getContents().clear();
        if (request.getContents() != null) {
            for (ServiceContentRequest cr : request.getContents()) {
                service.addContent(buildContent(cr));
            }
        }

        // ── Replace media collections — delete removed S3 files first ───────
        List<String> oldUrls = collectAllMediaUrls(service);
        service.getMediaCollections().clear();   // orphanRemoval handles DB

        if (request.getMediaCollections() != null) {
            int seq = 0;
            for (ServiceMediaCollectionRequest cr : request.getMediaCollections()) {
                service.addMediaCollection(buildCollection(cr, seq++));
            }
        }

        List<String> newUrls = collectAllMediaUrlsFromRequest(request);
        oldUrls.stream()
                .filter(url -> !newUrls.contains(url))
                .forEach(url -> {
                    s3Service.deleteFile(url);
                    log.info("Deleted removed media from S3: {}", url);
                });

        return toResponse(serviceRepository.save(service));
    }

    // =========================================================================
    // TOGGLE ACTIVE
    // =========================================================================

    @Transactional
    public ServiceResponse setActive(Long id, boolean active) {
        ak.dev.khi_backend.khi_app.model.service.Service service =
                serviceRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Service not found: " + id));
        service.setActive(active);
        return toResponse(serviceRepository.save(service));
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    @Transactional
    public void delete(Long id) {
        ak.dev.khi_backend.khi_app.model.service.Service service =
                serviceRepository.findByIdWithAll(id)
                        .orElseThrow(() -> new EntityNotFoundException("Service not found: " + id));

        if (service.getCoverMediaUrl() != null) {
            s3Service.deleteFile(service.getCoverMediaUrl());
        }

        service.getMediaCollections().forEach(col ->
                col.getFiles().forEach(f -> {
                    if (f.getFileUrl() != null)      s3Service.deleteFile(f.getFileUrl());
                    if (f.getThumbnailUrl() != null)  s3Service.deleteFile(f.getThumbnailUrl());
                })
        );

        serviceRepository.delete(service);
        log.info("Deleted service id={}", id);
    }

    // =========================================================================
    // COLLECTION MANAGEMENT
    // =========================================================================

    @Transactional
    public ServiceMediaCollectionResponse addCollection(Long serviceId,
                                                        CollectionUpsertRequest request) {
        ak.dev.khi_backend.khi_app.model.service.Service service =
                serviceRepository.findById(serviceId)
                        .orElseThrow(() -> new EntityNotFoundException("Service not found: " + serviceId));

        ServiceMediaCollection col = buildCollectionFromUpsert(
                request, service.getMediaCollections().size());
        service.addMediaCollection(col);
        serviceRepository.save(service);
        return toCollectionResponse(col);
    }

    @Transactional
    public ServiceMediaCollectionResponse updateCollection(Long serviceId,
                                                           Long collectionId,
                                                           CollectionUpsertRequest request) {
        ServiceMediaCollection col = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found: " + collectionId));

        if (!col.getService().getId().equals(serviceId)) {
            throw new IllegalArgumentException(
                    "Collection " + collectionId + " does not belong to service " + serviceId);
        }
        col.setCollectionName(trimRequired(request.getCollectionName(), "collectionName"));
        col.setMediaType(resolveMediaType(request.getMediaType()));
        if (request.getSortOrder() != null) col.setSortOrder(request.getSortOrder());

        return toCollectionResponse(collectionRepository.save(col));
    }

    @Transactional
    public void deleteCollection(Long serviceId, Long collectionId) {
        ServiceMediaCollection col = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found: " + collectionId));

        if (!col.getService().getId().equals(serviceId)) {
            throw new IllegalArgumentException(
                    "Collection " + collectionId + " does not belong to service " + serviceId);
        }
        col.getFiles().forEach(f -> {
            if (f.getFileUrl() != null)      s3Service.deleteFile(f.getFileUrl());
            if (f.getThumbnailUrl() != null)  s3Service.deleteFile(f.getThumbnailUrl());
        });

        collectionRepository.delete(col);
        log.info("Deleted collection id={} from service id={}", collectionId, serviceId);
    }

    // =========================================================================
    // FILE MANAGEMENT
    // =========================================================================

    /**
     * Add a pre-uploaded file to a collection.
     * Technical metadata is re-extracted from S3 to guarantee accuracy.
     */
    @Transactional
    public ServiceMediaFileResponse addFileToCollection(Long collectionId,
                                                        FileAddRequest request) {
        ServiceMediaCollection col = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found: " + collectionId));

        if (request.getFileUrl() == null || request.getFileUrl().isBlank()) {
            throw new IllegalArgumentException("fileUrl is required");
        }

        // Re-extract metadata from S3 so it is always accurate
        MediaFileMeta meta = fetchAndExtractFromS3(request.getFileUrl());

        ServiceMediaFile file = ServiceMediaFile.builder()
                .fileUrl(request.getFileUrl().trim())
                .thumbnailUrl(trimOrNull(request.getThumbnailUrl()))
                .ckbContent(buildFileContent(request.getCkbContent()))
                .kmrContent(buildFileContent(request.getKmrContent()))
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : col.getFiles().size())
                .fileFormat(meta.getFileFormat())
                .widthPx(meta.getWidthPx())
                .heightPx(meta.getHeightPx())
                .durationSeconds(meta.getDurationSeconds())
                .codec(meta.getCodec())
                .bitrateKbps(meta.getBitrateKbps())
                // fileSize not available when fetching from S3 URL — left null
                .build();

        col.addFile(file);
        collectionRepository.save(col);
        return toFileResponse(file);
    }

    @Transactional
    public void deleteFile(Long fileId) {
        ServiceMediaFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("Media file not found: " + fileId));

        if (file.getFileUrl() != null)      s3Service.deleteFile(file.getFileUrl());
        if (file.getThumbnailUrl() != null)  s3Service.deleteFile(file.getThumbnailUrl());

        fileRepository.delete(file);
        log.info("Deleted media file id={}", fileId);
    }

    // =========================================================================
    // MEDIA UPLOAD — S3  +  AUTO METADATA EXTRACTION
    // =========================================================================

    /**
     * Upload a single file to S3 and auto-extract all technical metadata.
     *
     * The returned {@link UploadResponse} includes:
     *   • fileUrl      — S3 URL to embed in ServiceMediaFileRequest
     *   • fileFormat   — "JPEG" / "MP4" / "MP3" …
     *   • widthPx / heightPx  — image and video resolution
     *   • durationSeconds     — video / audio length
     *   • codec / bitrateKbps — video / audio encoding info
     *
     * The admin panel should display all these as read-only fields — the user
     * never types them.
     *
     * @param file  uploaded multipart file
     * @param type  optional hint: "image" | "video" | "audio" | "gallery"
     */
    public UploadResponse uploadMedia(MultipartFile file, String type) throws IOException {

        log.info("Uploading service media: name={}, hint={}, contentType={}, size={}",
                file.getOriginalFilename(), type, file.getContentType(), file.getSize());

        byte[] bytes = file.getBytes();

        // ── 1. Extract metadata from raw bytes BEFORE uploading ───────────────
        MediaFileMeta meta = metadataExtractor.extract(
                bytes, file.getContentType(), file.getOriginalFilename());

        if (meta.hasData()) {
            log.debug("Extracted metadata: format={}, {}x{}, duration={}s, codec={}, bitrate={}kbps",
                    meta.getFileFormat(), meta.getWidthPx(), meta.getHeightPx(),
                    meta.getDurationSeconds(), meta.getCodec(), meta.getBitrateKbps());
        } else {
            log.warn("No metadata extracted for: {}", file.getOriginalFilename());
        }

        // ── 2. Upload to S3 ───────────────────────────────────────────────────
        ProjectMediaType mediaType = resolveProjectMediaType(type);
        String fileUrl = mediaType != null
                ? s3Service.upload(bytes, file.getOriginalFilename(),
                file.getContentType(), mediaType)
                : s3Service.upload(bytes, file.getOriginalFilename(),
                file.getContentType());

        log.info("Upload successful: {}", fileUrl);

        // ── 3. Build enriched response ────────────────────────────────────────
        String resolution       = buildResolution(meta.getWidthPx(), meta.getHeightPx());
        String formattedDuration = formatDuration(meta.getDurationSeconds());
        String formattedSize     = formatFileSize(file.getSize());

        return UploadResponse.builder()
                .fileUrl(fileUrl)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .formattedFileSize(formattedSize)
                .contentType(file.getContentType())
                .fileFormat(meta.getFileFormat())
                .widthPx(meta.getWidthPx())
                .heightPx(meta.getHeightPx())
                .resolution(resolution)
                .durationSeconds(meta.getDurationSeconds())
                .formattedDuration(formattedDuration)
                .codec(meta.getCodec())
                .bitrateKbps(meta.getBitrateKbps())
                .build();
    }

    /** Overload — no media type hint. */
    public UploadResponse uploadMedia(MultipartFile file) throws IOException {
        return uploadMedia(file, null);
    }

    /**
     * Bulk upload — all files in the batch share the same optional type hint.
     * Returns results in the same order as the input list.
     */
    public List<UploadResponse> uploadMultipleMedia(List<MultipartFile> files, String type) {
        return files.stream()
                .map(file -> {
                    try {
                        return uploadMedia(file, type);
                    } catch (IOException e) {
                        log.error("Upload failed: {}", file.getOriginalFilename(), e);
                        throw new RuntimeException(
                                "Upload failed for: " + file.getOriginalFilename(), e);
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

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    // ─── S3 re-extraction (for FileAddRequest flow) ───────────────────────────

    /**
     * Download a file from S3 by its URL and extract its technical metadata.
     * Used when a file is added to a collection via the standalone endpoint
     * (the original upload bytes are no longer in memory at that point).
     * If the download fails, returns {@link MediaFileMeta#empty()} gracefully.
     */
    private MediaFileMeta fetchAndExtractFromS3(String fileUrl) {
        try {
            byte[] bytes = s3Service.download(fileUrl);
            String contentType = guessContentTypeFromUrl(fileUrl);
            String filename    = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            return metadataExtractor.extract(bytes, contentType, filename);
        } catch (Exception e) {
            log.warn("Could not re-extract metadata from S3 URL {}: {}", fileUrl, e.getMessage());
            return MediaFileMeta.empty();
        }
    }

    /** Derive a MIME type from the file extension in the URL. Best-effort only. */
    private String guessContentTypeFromUrl(String url) {
        if (url == null) return null;
        String lower = url.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".mp4"))  return "video/mp4";
        if (lower.endsWith(".mov"))  return "video/quicktime";
        if (lower.endsWith(".avi"))  return "video/x-msvideo";
        if (lower.endsWith(".mkv"))  return "video/x-matroska";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3"))  return "audio/mpeg";
        if (lower.endsWith(".wav"))  return "audio/wav";
        if (lower.endsWith(".aac"))  return "audio/aac";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".ogg"))  return "audio/ogg";
        return null;
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    private void validateContents(List<ServiceContentRequest> contents) {
        if (contents == null || contents.isEmpty()) return;

        long distinctCodes = contents.stream()
                .map(c -> c.getLanguageCode() == null ? "" : c.getLanguageCode().toUpperCase())
                .distinct().count();

        if (distinctCodes < contents.size()) {
            throw new IllegalArgumentException("Duplicate language codes in contents list");
        }

        for (ServiceContentRequest cr : contents) {
            if (cr.getLanguageCode() == null || cr.getLanguageCode().isBlank()) {
                throw new IllegalArgumentException("languageCode is required for each content entry");
            }
            String code = cr.getLanguageCode().toUpperCase();
            if (!ALLOWED_LANG_CODES.contains(code)) {
                throw new IllegalArgumentException(
                        "Unsupported language code: " + cr.getLanguageCode()
                                + ". Accepted: " + ALLOWED_LANG_CODES);
            }
            if (cr.getTitle() == null || cr.getTitle().isBlank()) {
                throw new IllegalArgumentException(
                        "title is required for language: " + cr.getLanguageCode());
            }
        }
    }

    // ─── Entity Builders ──────────────────────────────────────────────────────

    private ServiceContent buildContent(ServiceContentRequest req) {
        return ServiceContent.builder()
                .languageCode(req.getLanguageCode().toUpperCase().trim())
                .title(req.getTitle().trim())
                .description(req.getDescription())
                .build();
    }

    private ServiceMediaCollection buildCollection(ServiceMediaCollectionRequest req, int defaultOrder) {
        ServiceMediaCollection col = ServiceMediaCollection.builder()
                .collectionName(trimRequired(req.getCollectionName(), "collectionName"))
                .mediaType(resolveMediaType(req.getMediaType()))
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : defaultOrder)
                .build();

        if (req.getFiles() != null) {
            int seq = 0;
            for (ServiceMediaFileRequest fr : req.getFiles()) {
                col.addFile(buildFileFromRequest(fr, seq++));
            }
        }
        return col;
    }

    private ServiceMediaCollection buildCollectionFromUpsert(CollectionUpsertRequest req, int defaultOrder) {
        return ServiceMediaCollection.builder()
                .collectionName(trimRequired(req.getCollectionName(), "collectionName"))
                .mediaType(resolveMediaType(req.getMediaType()))
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : defaultOrder)
                .build();
    }

    /**
     * Build a {@link ServiceMediaFile} from a request DTO.
     *
     * Technical metadata fields (format, resolution, codec…) are NOT in the
     * request.  When the service is created / updated in one shot, the admin
     * client should carry over the values returned by the upload endpoint.
     * We therefore intentionally leave those fields null here — they will be
     * populated via the upload flow (stored in DB after extraction).
     *
     * If you want guaranteed metadata, use {@link #addFileToCollection} which
     * re-downloads from S3 and re-extracts.
     */
    private ServiceMediaFile buildFileFromRequest(ServiceMediaFileRequest req, int defaultOrder) {
        if (req.getFileUrl() == null || req.getFileUrl().isBlank()) {
            throw new IllegalArgumentException("fileUrl is required for each media file");
        }
        return ServiceMediaFile.builder()
                .fileUrl(req.getFileUrl().trim())
                .thumbnailUrl(trimOrNull(req.getThumbnailUrl()))
                .ckbContent(buildFileContent(req.getCkbContent()))
                .kmrContent(buildFileContent(req.getKmrContent()))
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : defaultOrder)
                // Technical metadata not in request — populated at upload time
                .build();
    }

    private ServiceMediaFileContent buildFileContent(FileContentRequest req) {
        if (req == null) return new ServiceMediaFileContent();
        return ServiceMediaFileContent.builder()
                .caption(req.getCaption())
                .title(req.getTitle())
                .description(req.getDescription())
                .build();
    }

    // ─── S3 URL collection (for orphan cleanup on update) ────────────────────

    private List<String> collectAllMediaUrls(ak.dev.khi_backend.khi_app.model.service.Service service) {
        return service.getMediaCollections().stream()
                .flatMap(col -> col.getFiles().stream())
                .flatMap(f -> {
                    List<String> urls = new java.util.ArrayList<>();
                    if (f.getFileUrl() != null)     urls.add(f.getFileUrl());
                    if (f.getThumbnailUrl() != null) urls.add(f.getThumbnailUrl());
                    return urls.stream();
                })
                .collect(Collectors.toList());
    }

    private List<String> collectAllMediaUrlsFromRequest(ServiceRequest request) {
        if (request.getMediaCollections() == null) return List.of();
        return request.getMediaCollections().stream()
                .filter(c -> c.getFiles() != null)
                .flatMap(c -> c.getFiles().stream())
                .flatMap(f -> {
                    List<String> urls = new java.util.ArrayList<>();
                    if (f.getFileUrl() != null)     urls.add(f.getFileUrl());
                    if (f.getThumbnailUrl() != null) urls.add(f.getThumbnailUrl());
                    return urls.stream();
                })
                .collect(Collectors.toList());
    }

    // ─── Type Resolvers ───────────────────────────────────────────────────────

    private ServiceMediaCollection.MediaType resolveMediaType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("mediaType is required for a collection");
        }
        try {
            return ServiceMediaCollection.MediaType.valueOf(raw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid mediaType: " + raw + ". Accepted: IMAGE, VIDEO, AUDIO");
        }
    }

    private ProjectMediaType resolveProjectMediaType(String hint) {
        if (hint == null) return null;
        return switch (hint.toLowerCase().trim()) {
            case "image", "gallery" -> ProjectMediaType.IMAGE;
            case "video"            -> ProjectMediaType.VIDEO;
            case "audio"            -> ProjectMediaType.AUDIO;
            default                 -> null;
        };
    }

    // ─── Formatting ───────────────────────────────────────────────────────────

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw, FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid publishedAt format. Expected: yyyy-MM-dd HH:mm:ss, got: " + raw);
        }
    }

    private String buildResolution(Integer w, Integer h) {
        return (w != null && h != null) ? w + " × " + h : null;
    }

    private String formatDuration(Integer seconds) {
        if (seconds == null) return null;
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s)
                : String.format("%d:%02d", m, s);
    }

    private String formatFileSize(long bytes) {
        if (bytes >= 1_073_741_824L) return String.format("%.1f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576L)     return String.format("%.1f MB", bytes / 1_048_576.0);
        if (bytes >= 1_024L)         return String.format("%.1f KB", bytes / 1_024.0);
        return bytes + " B";
    }

    private String trimRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private String trimOrNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // =========================================================================
    // RESPONSE MAPPERS
    // =========================================================================

    private ServiceResponse toResponse(ak.dev.khi_backend.khi_app.model.service.Service service) {
        return ServiceResponse.builder()
                .id(service.getId())
                .serviceType(service.getServiceType())
                .location(service.getLocation())
                .coverMediaUrl(service.getCoverMediaUrl())
                .active(service.isActive())
                .publishedAt(service.getPublishedAt() != null
                        ? service.getPublishedAt().format(FORMATTER) : null)
                .contents(service.getContents().stream()
                        .map(this::toContentResponse)
                        .collect(Collectors.toList()))
                .mediaCollections(service.getMediaCollections().stream()
                        .sorted(java.util.Comparator.comparingInt(ServiceMediaCollection::getSortOrder))
                        .map(this::toCollectionResponse)
                        .collect(Collectors.toList()))
                .createdAt(service.getCreatedAt() != null
                        ? service.getCreatedAt().format(FORMATTER) : null)
                .updatedAt(service.getUpdatedAt() != null
                        ? service.getUpdatedAt().format(FORMATTER) : null)
                .build();
    }

    private ServiceContentResponse toContentResponse(ServiceContent c) {
        return ServiceContentResponse.builder()
                .id(c.getId())
                .languageCode(c.getLanguageCode())
                .title(c.getTitle())
                .description(c.getDescription())
                .build();
    }

    private ServiceMediaCollectionResponse toCollectionResponse(ServiceMediaCollection col) {
        return ServiceMediaCollectionResponse.builder()
                .id(col.getId())
                .collectionName(col.getCollectionName())
                .mediaType(col.getMediaType().name())
                .sortOrder(col.getSortOrder())
                .createdAt(col.getCreatedAt() != null
                        ? col.getCreatedAt().format(FORMATTER) : null)
                .files(col.getFiles().stream()
                        .sorted(java.util.Comparator.comparingInt(ServiceMediaFile::getSortOrder))
                        .map(this::toFileResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    private ServiceMediaFileResponse toFileResponse(ServiceMediaFile f) {
        return ServiceMediaFileResponse.builder()
                .id(f.getId())
                .fileUrl(f.getFileUrl())
                .thumbnailUrl(f.getThumbnailUrl())
                .ckbContent(toFileContentResponse(f.getCkbContent()))
                .kmrContent(toFileContentResponse(f.getKmrContent()))
                // ── Technical metadata (auto-extracted, read-only) ─────────────
                .fileFormat(f.getFileFormat())
                .widthPx(f.getWidthPx())
                .heightPx(f.getHeightPx())
                .resolution(f.getResolution())
                .durationSeconds(f.getDurationSeconds())
                .formattedDuration(f.getFormattedDuration())
                .codec(f.getCodec())
                .bitrateKbps(f.getBitrateKbps())
                .fileSize(f.getFileSize())
                .formattedFileSize(f.getFormattedFileSize())
                // ──────────────────────────────────────────────────────────────
                .sortOrder(f.getSortOrder())
                .createdAt(f.getCreatedAt() != null
                        ? f.getCreatedAt().format(FORMATTER) : null)
                .build();
    }

    private FileContentResponse toFileContentResponse(ServiceMediaFileContent c) {
        if (c == null) return null;
        return FileContentResponse.builder()
                .caption(c.getCaption())
                .title(c.getTitle())
                .description(c.getDescription())
                .build();
    }
}