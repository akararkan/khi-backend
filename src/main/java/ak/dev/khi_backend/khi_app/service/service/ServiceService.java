package ak.dev.khi_backend.khi_app.service.service;

import ak.dev.khi_backend.khi_app.dto.service.ServiceDTOs.*;
import ak.dev.khi_backend.khi_app.enums.project.ProjectMediaType;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.exceptions.NotFoundException;
import ak.dev.khi_backend.khi_app.model.service.*;
import ak.dev.khi_backend.khi_app.repository.service.*;
import ak.dev.khi_backend.khi_app.service.MediaMetadataExtractor;
import ak.dev.khi_backend.khi_app.service.MediaMetadataExtractor.MediaFileMeta;
import ak.dev.khi_backend.khi_app.service.S3Service;
import ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * ServiceService — Business logic for the Service module.
 *
 * ─── Responsibilities ─────────────────────────────────────────────────────────
 *  • Full CRUD for {@link ak.dev.khi_backend.khi_app.model.service.Service}.
 *  • Bilingual content management (CKB / KMR rows in service_contents).
 *  • Media collection + file management with full S3 lifecycle.
 *  • Automatic technical metadata extraction via {@link MediaMetadataExtractor}.
 *  • Two-phase pagination (ID scan → batch hydration) to avoid N+1.
 *  • Redis caching on read paths with eviction on every write.
 *  • Audit logging for all CUD operations.
 *  • Parallel S3 cleanup on update/delete for fast execution.
 *  • Global search across service type, location, and bilingual content.
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
    private final ServiceAuditLogRepository        auditLogRepository;
    private final S3Service                        s3Service;
    private final MediaMetadataExtractor           metadataExtractor;
    private final TiptapHtmlProcessor              tiptapHtmlProcessor;

    // =========================================================================
    // READ — Paginated + Cached (Two-Phase Hydration)
    //
    // Execution plan for a page of 20:
    //   Q1: SELECT id FROM services ORDER BY published_at DESC  (Phase 1)
    //   Q2: SELECT s  FROM services WHERE id IN (...)           (Phase 2)
    //   Q3–Q5: @BatchSize fires 1 IN-query per collection type
    //   Total: ~5 fast queries. Cache hit: <5ms.
    // =========================================================================

    @Cacheable(value = "services", key = "'active:p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<ServiceResponse> getAllActive(int page, int size) {
        Page<Long> idPage = serviceRepository.findActiveIds(PageRequest.of(page, size));
        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }
        List<ak.dev.khi_backend.khi_app.model.service.Service> hydrated =
                hydrateAndSort(idPage.getContent());
        return new PageImpl<>(
                hydrated.stream().map(this::toResponse).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    /** Admin view — includes inactive services. */
    @Cacheable(value = "services", key = "'all:p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<ServiceResponse> getAll(int page, int size) {
        Page<Long> idPage = serviceRepository.findAllIds(PageRequest.of(page, size));
        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }
        List<ak.dev.khi_backend.khi_app.model.service.Service> hydrated =
                hydrateAndSort(idPage.getContent());
        return new PageImpl<>(
                hydrated.stream().map(this::toResponse).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    /** Active services by type — paginated, cached. */
    @Cacheable(value = "services",
            key = "'type:' + #serviceType.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<ServiceResponse> getAllActiveByType(String serviceType, int page, int size) {
        if (serviceType == null || serviceType.isBlank()) {
            throw new BadRequestException("service.type.required",
                    Map.of("field", "serviceType"));
        }
        Page<Long> idPage = serviceRepository.findActiveIdsByType(
                serviceType.trim(), PageRequest.of(page, size));
        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }
        List<ak.dev.khi_backend.khi_app.model.service.Service> hydrated =
                hydrateAndSort(idPage.getContent());
        return new PageImpl<>(
                hydrated.stream().map(this::toResponse).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    /**
     * Global search — searches service type, location, bilingual title/description.
     */
    @Cacheable(value = "services",
            key = "'search:' + #q.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<ServiceResponse> globalSearch(String q, int page, int size) {
        if (q == null || q.isBlank()) {
            throw new BadRequestException("service.search.required",
                    Map.of("field", "q"));
        }
        Page<Long> idPage = serviceRepository.findIdsByGlobalSearch(
                q.trim(), PageRequest.of(page, size));
        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }
        List<ak.dev.khi_backend.khi_app.model.service.Service> hydrated =
                hydrateAndSort(idPage.getContent());
        return new PageImpl<>(
                hydrated.stream().map(this::toResponse).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    /** Admin search — includes inactive. */
    @Cacheable(value = "services",
            key = "'adminSearch:' + #q.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<ServiceResponse> adminSearch(String q, int page, int size) {
        if (q == null || q.isBlank()) {
            throw new BadRequestException("service.search.required",
                    Map.of("field", "q"));
        }
        Page<Long> idPage = serviceRepository.findIdsByAdminSearch(
                q.trim(), PageRequest.of(page, size));
        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        }
        List<ak.dev.khi_backend.khi_app.model.service.Service> hydrated =
                hydrateAndSort(idPage.getContent());
        return new PageImpl<>(
                hydrated.stream().map(this::toResponse).collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements()
        );
    }

    /** Full detail view for a single service by ID. */
    @Transactional(readOnly = true)
    public ServiceResponse getById(Long id) {
        ak.dev.khi_backend.khi_app.model.service.Service service =
                serviceRepository.findByIdWithAll(id)
                        .orElseThrow(() -> new NotFoundException(
                                "service.not_found", Map.of("id", id)));
        return toResponse(service);
    }

    /** Distinct service types in the DB (for filter dropdowns). */
    @Cacheable(value = "services", key = "'types'")
    @Transactional(readOnly = true)
    public List<String> getServiceTypes() {
        return serviceRepository.findDistinctServiceTypes();
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public ServiceResponse create(ServiceRequest request) {
        String traceId = traceId();
        log.info("Creating service | type={} | traceId={}", request.getServiceType(), traceId);

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

        ak.dev.khi_backend.khi_app.model.service.Service saved =
                serviceRepository.save(service);

        auditLog(saved, "CREATE", "Service created: " + saved.getServiceType(), traceId);
        log.info("Service created | id={} | traceId={}", saved.getId(), traceId);

        return toResponse(saved);
    }

    // =========================================================================
    // CREATE WITH FILES (multipart — upload + save in one request)
    // =========================================================================

    /**
     * Creates a service AND uploads media files in a single request.
     *
     * <p>Auto-generated collections ("Images", "Videos", "Audios") are appended
     * after any collections already declared in the JSON {@code request} part.</p>
     *
     * @param request  service JSON payload (may already contain pre-uploaded mediaCollections)
     * @param cover    optional cover image file — uploaded to S3, URL set as coverMediaUrl
     * @param images   optional collection of image files
     * @param videos   optional collection of video files
     * @param audios   optional collection of audio files
     */
    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public ServiceResponse createWithFiles(ServiceRequest request,
                                           MultipartFile cover,
                                           List<MultipartFile> images,
                                           List<MultipartFile> videos,
                                           List<MultipartFile> audios) throws IOException {
        String traceId = traceId();
        log.info("Creating service with files | type={} | traceId={}", request.getServiceType(), traceId);

        validateContents(request.getContents());

        // 1. Upload cover to S3 if provided
        String coverUrl = uploadCoverIfPresent(cover, request.getCoverMediaUrl());

        // 2. Build base entity
        ak.dev.khi_backend.khi_app.model.service.Service service =
                ak.dev.khi_backend.khi_app.model.service.Service.builder()
                        .serviceType(trimRequired(request.getServiceType(), "serviceType"))
                        .location(trimOrNull(request.getLocation()))
                        .coverMediaUrl(coverUrl)
                        .active(true)
                        .publishedAt(parseDateTime(request.getPublishedAt()))
                        .build();

        // 3. Add bilingual content
        if (request.getContents() != null) {
            for (ServiceContentRequest cr : request.getContents()) {
                service.addContent(buildContent(cr));
            }
        }

        // 4. Add pre-existing collections from JSON
        int seq = 0;
        if (request.getMediaCollections() != null) {
            for (ServiceMediaCollectionRequest cr : request.getMediaCollections()) {
                service.addMediaCollection(buildCollection(cr, seq++));
            }
        }

        // 5. Upload files in parallel and build auto-generated collections
        appendUploadedCollections(service, images, videos, audios, seq);

        // 6. Persist
        ak.dev.khi_backend.khi_app.model.service.Service saved =
                serviceRepository.save(service);

        auditLog(saved, "CREATE", "Service created (with files): " + saved.getServiceType(), traceId);
        log.info("Service created with files | id={} | traceId={}", saved.getId(), traceId);

        return toResponse(saved);
    }

    // =========================================================================
    // UPDATE WITH FILES (multipart)
    // =========================================================================

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public ServiceResponse updateWithFiles(Long id,
                                           ServiceRequest request,
                                           MultipartFile cover,
                                           List<MultipartFile> images,
                                           List<MultipartFile> videos,
                                           List<MultipartFile> audios) throws IOException {
        String traceId = traceId();
        log.info("Updating service with files | id={} | traceId={}", id, traceId);

        ak.dev.khi_backend.khi_app.model.service.Service service =
                serviceRepository.findByIdWithAll(id)
                        .orElseThrow(() -> new NotFoundException(
                                "service.not_found", Map.of("id", id)));

        validateContents(request.getContents());

        // ── Collect ALL old S3 URLs before mutation ──────────────────────────
        List<String> oldUrls = collectAllMediaUrls(service);
        String oldCover = service.getCoverMediaUrl();

        // ── Upload cover ─────────────────────────────────────────────────────
        String newCover = uploadCoverIfPresent(cover, request.getCoverMediaUrl());

        // ── Scalar fields ────────────────────────────────────────────────────
        service.setServiceType(trimRequired(request.getServiceType(), "serviceType"));
        service.setLocation(trimOrNull(request.getLocation()));
        service.setPublishedAt(parseDateTime(request.getPublishedAt()));
        service.setCoverMediaUrl(newCover);

        // ── Replace content + collections ────────────────────────────────────
        service.getContents().clear();
        service.getMediaCollections().clear();
        serviceRepository.saveAndFlush(service);

        if (request.getContents() != null) {
            for (ServiceContentRequest cr : request.getContents()) {
                service.addContent(buildContent(cr));
            }
        }

        int seq = 0;
        if (request.getMediaCollections() != null) {
            for (ServiceMediaCollectionRequest cr : request.getMediaCollections()) {
                service.addMediaCollection(buildCollection(cr, seq++));
            }
        }

        // ── Upload files + append auto-generated collections ─────────────────
        appendUploadedCollections(service, images, videos, audios, seq);

        ak.dev.khi_backend.khi_app.model.service.Service saved =
                serviceRepository.save(service);

        // ── Parallel S3 orphan cleanup ──────────────────────────────────────
        List<String> newUrls = collectAllMediaUrls(saved);
        if (saved.getCoverMediaUrl() != null) newUrls.add(saved.getCoverMediaUrl());

        List<String> orphanUrls = new ArrayList<>();
        if (oldCover != null && !oldCover.equals(newCover)) {
            orphanUrls.add(oldCover);
        }
        oldUrls.stream()
                .filter(url -> !newUrls.contains(url))
                .forEach(orphanUrls::add);

        if (!orphanUrls.isEmpty()) {
            deleteS3FilesParallel(orphanUrls, traceId);
        }

        auditLog(saved, "UPDATE", "Service updated (with files): " + saved.getServiceType(), traceId);
        log.info("Service updated with files | id={} | orphansCleaned={} | traceId={}",
                saved.getId(), orphanUrls.size(), traceId);

        return toResponse(saved);
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public ServiceResponse update(Long id, ServiceRequest request) {
        String traceId = traceId();
        log.info("Updating service | id={} | traceId={}", id, traceId);

        ak.dev.khi_backend.khi_app.model.service.Service service =
                serviceRepository.findByIdWithAll(id)
                        .orElseThrow(() -> new NotFoundException(
                                "service.not_found", Map.of("id", id)));

        validateContents(request.getContents());

        // ── Collect ALL old S3 URLs before mutation ──────────────────────────
        List<String> oldUrls = collectAllMediaUrls(service);
        String oldCover = service.getCoverMediaUrl();

        // ── Scalar fields ───────────────────────────────────────────────────
        service.setServiceType(trimRequired(request.getServiceType(), "serviceType"));
        service.setLocation(trimOrNull(request.getLocation()));
        service.setPublishedAt(parseDateTime(request.getPublishedAt()));

        String newCover = trimOrNull(request.getCoverMediaUrl());
        service.setCoverMediaUrl(newCover);

        // ── Replace bilingual content rows + media collections ────────────
        // Clear first, then flush to force DELETEs before INSERTs
        // (prevents unique-constraint violations on re-insert)
        service.getContents().clear();
        service.getMediaCollections().clear();
        serviceRepository.saveAndFlush(service);

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

        ak.dev.khi_backend.khi_app.model.service.Service saved =
                serviceRepository.save(service);

        // ── Parallel S3 orphan cleanup ──────────────────────────────────────
        List<String> newUrls = collectAllMediaUrlsFromRequest(request);
        if (newCover != null) newUrls.add(newCover);

        List<String> orphanUrls = new ArrayList<>();
        if (oldCover != null && !oldCover.equals(newCover)) {
            orphanUrls.add(oldCover);
        }
        oldUrls.stream()
                .filter(url -> !newUrls.contains(url))
                .forEach(orphanUrls::add);

        if (!orphanUrls.isEmpty()) {
            deleteS3FilesParallel(orphanUrls, traceId);
        }

        auditLog(saved, "UPDATE", "Service updated: " + saved.getServiceType(), traceId);
        log.info("Service updated | id={} | orphansCleaned={} | traceId={}",
                saved.getId(), orphanUrls.size(), traceId);

        return toResponse(saved);
    }

    // =========================================================================
    // TOGGLE ACTIVE
    // =========================================================================

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public ServiceResponse setActive(Long id, boolean active) {
        String traceId = traceId();
        log.info("Toggling active | id={} | active={} | traceId={}", id, active, traceId);

        ak.dev.khi_backend.khi_app.model.service.Service service =
                serviceRepository.findById(id)
                        .orElseThrow(() -> new NotFoundException(
                                "service.not_found", Map.of("id", id)));

        service.setActive(active);
        ak.dev.khi_backend.khi_app.model.service.Service saved =
                serviceRepository.save(service);

        auditLog(saved, "TOGGLE_ACTIVE",
                "Service " + (active ? "activated" : "deactivated"), traceId);

        return toResponse(saved);
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public void delete(Long id) {
        String traceId = traceId();
        log.info("Deleting service | id={} | traceId={}", id, traceId);

        if (id == null) {
            throw new BadRequestException("service.id.required",
                    Map.of("field", "id"));
        }

        ak.dev.khi_backend.khi_app.model.service.Service service =
                serviceRepository.findByIdWithAll(id)
                        .orElseThrow(() -> new NotFoundException(
                                "service.not_found", Map.of("id", id)));

        // Audit BEFORE delete (captures snapshot)
        auditLog(service, "DELETE",
                "Service deleted: " + service.getServiceType(), traceId);

        // Collect all S3 URLs to clean up
        List<String> s3Urls = new ArrayList<>();
        if (service.getCoverMediaUrl() != null) {
            s3Urls.add(service.getCoverMediaUrl());
        }
        service.getMediaCollections().forEach(col ->
                col.getFiles().forEach(f -> {
                    if (f.getFileUrl() != null)      s3Urls.add(f.getFileUrl());
                    if (f.getThumbnailUrl() != null)  s3Urls.add(f.getThumbnailUrl());
                })
        );

        serviceRepository.delete(service);

        // Parallel S3 cleanup
        if (!s3Urls.isEmpty()) {
            deleteS3FilesParallel(s3Urls, traceId);
        }

        log.info("Service deleted | id={} | s3FilesRemoved={} | traceId={}",
                id, s3Urls.size(), traceId);
    }

    /** Bulk delete — multiple services at once with parallel S3 cleanup. */
    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public void deleteBulk(List<Long> ids) {
        String traceId = traceId();
        log.info("Bulk deleting services | count={} | traceId={}", ids.size(), traceId);

        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("service.ids.required",
                    Map.of("field", "ids"));
        }

        List<ak.dev.khi_backend.khi_app.model.service.Service> services =
                serviceRepository.findAllById(ids);

        if (services.isEmpty()) {
            throw new NotFoundException("service.not_found",
                    Map.of("ids", ids));
        }

        // Audit BEFORE delete
        auditLogRepository.saveAll(
                services.stream()
                        .map(s -> buildAuditLog(s, "DELETE",
                                "Service bulk-deleted: " + s.getServiceType(), traceId))
                        .toList()
        );

        // Collect all S3 URLs
        List<String> s3Urls = new ArrayList<>();
        for (var svc : services) {
            if (svc.getCoverMediaUrl() != null) s3Urls.add(svc.getCoverMediaUrl());
            try {
                svc.getMediaCollections().forEach(col ->
                        col.getFiles().forEach(f -> {
                            if (f.getFileUrl() != null) s3Urls.add(f.getFileUrl());
                            if (f.getThumbnailUrl() != null) s3Urls.add(f.getThumbnailUrl());
                        })
                );
            } catch (Exception e) {
                log.warn("Could not load media for bulk-delete svc id={}: {}",
                        svc.getId(), e.getMessage());
            }
        }

        serviceRepository.deleteAll(services);

        if (!s3Urls.isEmpty()) {
            deleteS3FilesParallel(s3Urls, traceId);
        }

        log.info("Bulk delete complete | deleted={} | s3FilesRemoved={} | traceId={}",
                services.size(), s3Urls.size(), traceId);
    }

    // =========================================================================
    // COLLECTION MANAGEMENT
    // =========================================================================

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public ServiceMediaCollectionResponse addCollection(Long serviceId,
                                                        CollectionUpsertRequest request) {
        String traceId = traceId();
        log.info("Adding collection | serviceId={} | traceId={}", serviceId, traceId);

        ak.dev.khi_backend.khi_app.model.service.Service service =
                serviceRepository.findById(serviceId)
                        .orElseThrow(() -> new NotFoundException(
                                "service.not_found", Map.of("id", serviceId)));

        ServiceMediaCollection col = buildCollectionFromUpsert(
                request, service.getMediaCollections().size());
        service.addMediaCollection(col);
        serviceRepository.save(service);

        auditLog(service, "UPDATE",
                "Collection added: " + col.getCollectionName(), traceId);

        return toCollectionResponse(col);
    }

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public ServiceMediaCollectionResponse updateCollection(Long serviceId,
                                                           Long collectionId,
                                                           CollectionUpsertRequest request) {
        String traceId = traceId();
        log.info("Updating collection | serviceId={} | colId={} | traceId={}",
                serviceId, collectionId, traceId);

        ServiceMediaCollection col = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new NotFoundException(
                        "service.collection.not_found", Map.of("id", collectionId)));

        if (!col.getService().getId().equals(serviceId)) {
            throw new BadRequestException("service.collection.mismatch",
                    Map.of("collectionId", collectionId, "serviceId", serviceId));
        }

        col.setCollectionName(trimRequired(request.getCollectionName(), "collectionName"));
        ServiceMediaCollection.MediaType type = resolveMediaType(request.getMediaType());
        if (type == null) {
            throw new BadRequestException("service.collection.mediaType.required",
                    Map.of("field", "mediaType"));
        }
        col.setMediaType(type);
        if (request.getSortOrder() != null) col.setSortOrder(request.getSortOrder());

        ServiceMediaCollection saved = collectionRepository.save(col);

        auditLog(col.getService(), "UPDATE",
                "Collection updated: " + saved.getCollectionName(), traceId);

        return toCollectionResponse(saved);
    }

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public void deleteCollection(Long serviceId, Long collectionId) {
        String traceId = traceId();
        log.info("Deleting collection | serviceId={} | colId={} | traceId={}",
                serviceId, collectionId, traceId);

        ServiceMediaCollection col = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new NotFoundException(
                        "service.collection.not_found", Map.of("id", collectionId)));

        if (!col.getService().getId().equals(serviceId)) {
            throw new BadRequestException("service.collection.mismatch",
                    Map.of("collectionId", collectionId, "serviceId", serviceId));
        }

        List<String> s3Urls = new ArrayList<>();
        col.getFiles().forEach(f -> {
            if (f.getFileUrl() != null)      s3Urls.add(f.getFileUrl());
            if (f.getThumbnailUrl() != null)  s3Urls.add(f.getThumbnailUrl());
        });

        auditLog(col.getService(), "UPDATE",
                "Collection deleted: " + col.getCollectionName(), traceId);

        collectionRepository.delete(col);

        if (!s3Urls.isEmpty()) {
            deleteS3FilesParallel(s3Urls, traceId);
        }

        log.info("Collection deleted | id={} | s3FilesRemoved={} | traceId={}",
                collectionId, s3Urls.size(), traceId);
    }

    // =========================================================================
    // FILE MANAGEMENT
    // =========================================================================

    /**
     * Add a pre-uploaded file to a collection.
     * Technical metadata is re-extracted from S3 to guarantee accuracy.
     */
    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public ServiceMediaFileResponse addFileToCollection(Long collectionId,
                                                        FileAddRequest request) {
        log.info("Adding file to collection | collectionId={}", collectionId);

        ServiceMediaCollection col = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new NotFoundException(
                        "service.collection.not_found", Map.of("id", collectionId)));

        if (request.getFileUrl() == null || request.getFileUrl().isBlank()) {
            throw new BadRequestException("service.file.url.required",
                    Map.of("field", "fileUrl"));
        }

        // Re-extract metadata from S3 so it is always accurate
        S3MetaResult metaResult = fetchAndExtractFromS3(request.getFileUrl());
        MediaFileMeta meta = metaResult.meta();

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
                .fileSize(metaResult.fileSize())
                .build();

        col.addFile(file);
        collectionRepository.save(col);
        return toFileResponse(file);
    }

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public void deleteFile(Long fileId) {
        String traceId = traceId();
        log.info("Deleting media file | fileId={} | traceId={}", fileId, traceId);

        ServiceMediaFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new NotFoundException(
                        "service.file.not_found", Map.of("id", fileId)));

        List<String> s3Urls = new ArrayList<>();
        if (file.getFileUrl() != null)      s3Urls.add(file.getFileUrl());
        if (file.getThumbnailUrl() != null)  s3Urls.add(file.getThumbnailUrl());

        fileRepository.delete(file);

        if (!s3Urls.isEmpty()) {
            deleteS3FilesParallel(s3Urls, traceId);
        }

        log.info("Media file deleted | id={} | traceId={}", fileId, traceId);
    }

    // =========================================================================
    // MEDIA UPLOAD — S3  +  AUTO METADATA EXTRACTION
    // =========================================================================

    /**
     * Upload a single file to S3 and auto-extract all technical metadata.
     *
     * @param file  uploaded multipart file
     * @param type  optional hint: "image" | "video" | "audio" | "gallery"
     */
    public UploadResponse uploadMedia(MultipartFile file, String type) throws IOException {

        log.info("Uploading service media: name={}, hint={}, contentType={}, size={}",
                file.getOriginalFilename(), type, file.getContentType(), file.getSize());

        byte[] bytes = file.getBytes();

        // 1. Extract metadata from raw bytes BEFORE uploading
        MediaFileMeta meta = metadataExtractor.extract(
                bytes, file.getContentType(), file.getOriginalFilename());

        if (meta.hasData()) {
            log.debug("Extracted metadata: format={}, {}x{}, duration={}s, codec={}, bitrate={}kbps",
                    meta.getFileFormat(), meta.getWidthPx(), meta.getHeightPx(),
                    meta.getDurationSeconds(), meta.getCodec(), meta.getBitrateKbps());
        } else {
            log.warn("No metadata extracted for: {}", file.getOriginalFilename());
        }

        // 2. Upload to S3
        ProjectMediaType mediaType = resolveProjectMediaType(type);
        String fileUrl = mediaType != null
                ? s3Service.upload(bytes, file.getOriginalFilename(),
                file.getContentType(), mediaType)
                : s3Service.upload(bytes, file.getOriginalFilename(),
                file.getContentType());

        log.info("Upload successful: {}", fileUrl);

        // 3. Build enriched response
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
     * Bulk upload — all files share the same optional type hint.
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
    // PRIVATE — Hydration (Phase-2)
    // =========================================================================

    private List<ak.dev.khi_backend.khi_app.model.service.Service> hydrateAndSort(List<Long> ids) {
        List<ak.dev.khi_backend.khi_app.model.service.Service> entities =
                serviceRepository.findAllByIds(ids);

        Map<Long, ak.dev.khi_backend.khi_app.model.service.Service> byId =
                entities.stream().collect(Collectors.toMap(
                        ak.dev.khi_backend.khi_app.model.service.Service::getId,
                        s -> s));

        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // PRIVATE — Parallel S3 Cleanup
    // =========================================================================

    private void deleteS3FilesParallel(List<String> urls, String traceId) {
        if (urls.isEmpty()) return;

        int poolSize = Math.min(8, urls.size());
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        try {
            List<CompletableFuture<Void>> futures = urls.stream()
                    .map(url -> CompletableFuture.runAsync(() -> {
                        try {
                            s3Service.deleteFile(url);
                            log.debug("S3 deleted: {} | traceId={}", url, traceId);
                        } catch (Exception e) {
                            log.warn("S3 delete failed: {} | error={} | traceId={}",
                                    url, e.getMessage(), traceId);
                        }
                    }, pool))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            pool.shutdown();
        }
    }

    // =========================================================================
    // PRIVATE — S3 Re-extraction
    // =========================================================================

    private S3MetaResult fetchAndExtractFromS3(String fileUrl) {
        try {
            byte[] bytes = s3Service.download(fileUrl);
            String contentType = guessContentTypeFromUrl(fileUrl);
            String filename    = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            MediaFileMeta meta = metadataExtractor.extract(bytes, contentType, filename);
            return new S3MetaResult(meta, (long) bytes.length);
        } catch (Exception e) {
            log.warn("Could not re-extract metadata from S3 URL {}: {}", fileUrl, e.getMessage());
            return new S3MetaResult(MediaFileMeta.empty(), null);
        }
    }

    private record S3MetaResult(MediaFileMeta meta, Long fileSize) {}

    // =========================================================================
    // PRIVATE — Multipart Upload Helpers
    // =========================================================================

    /**
     * Upload a cover image file to S3 if present, otherwise fall back to the
     * existing URL from the JSON payload.
     */
    private String uploadCoverIfPresent(MultipartFile cover, String fallbackUrl) throws IOException {
        if (cover != null && !cover.isEmpty()) {
            UploadResponse uploaded = uploadMedia(cover, "image");
            return uploaded.getFileUrl();
        }
        return trimOrNull(fallbackUrl);
    }

    /**
     * Upload image/video/audio file lists in parallel and append auto-generated
     * collections to the service entity.
     */
    private void appendUploadedCollections(ak.dev.khi_backend.khi_app.model.service.Service service,
                                           List<MultipartFile> images,
                                           List<MultipartFile> videos,
                                           List<MultipartFile> audios,
                                           int startOrder) {
        int seq = startOrder;

        if (images != null && !images.isEmpty()) {
            List<UploadResponse> uploaded = uploadFilesParallel(images, "image");
            service.addMediaCollection(
                    buildCollectionFromUploads("Images", ServiceMediaCollection.MediaType.IMAGE, uploaded, seq++));
        }

        if (videos != null && !videos.isEmpty()) {
            List<UploadResponse> uploaded = uploadFilesParallel(videos, "video");
            service.addMediaCollection(
                    buildCollectionFromUploads("Videos", ServiceMediaCollection.MediaType.VIDEO, uploaded, seq++));
        }

        if (audios != null && !audios.isEmpty()) {
            List<UploadResponse> uploaded = uploadFilesParallel(audios, "audio");
            service.addMediaCollection(
                    buildCollectionFromUploads("Audios", ServiceMediaCollection.MediaType.AUDIO, uploaded, seq));
        }
    }

    /**
     * Upload multiple files to S3 in parallel, returning metadata for each.
     */
    private List<UploadResponse> uploadFilesParallel(List<MultipartFile> files, String typeHint) {
        int poolSize = Math.min(8, files.size());
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        try {
            List<CompletableFuture<UploadResponse>> futures = files.stream()
                    .map(file -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return uploadMedia(file, typeHint);
                        } catch (IOException e) {
                            log.error("Upload failed: {}", file.getOriginalFilename(), e);
                            throw new CompletionException(e);
                        }
                    }, pool))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Convert a list of S3 upload results into a fully populated
     * {@link ServiceMediaCollection} entity with {@link ServiceMediaFile} children.
     */
    private ServiceMediaCollection buildCollectionFromUploads(String collectionName,
                                                              ServiceMediaCollection.MediaType mediaType,
                                                              List<UploadResponse> uploads,
                                                              int sortOrder) {
        ServiceMediaCollection col = ServiceMediaCollection.builder()
                .collectionName(collectionName)
                .mediaType(mediaType)
                .sortOrder(sortOrder)
                .build();

        int fileSeq = 0;
        for (UploadResponse up : uploads) {
            ServiceMediaFile file = ServiceMediaFile.builder()
                    .fileUrl(up.getFileUrl())
                    .fileFormat(up.getFileFormat())
                    .widthPx(up.getWidthPx())
                    .heightPx(up.getHeightPx())
                    .durationSeconds(up.getDurationSeconds())
                    .codec(up.getCodec())
                    .bitrateKbps(up.getBitrateKbps())
                    .fileSize(up.getFileSize())
                    .sortOrder(fileSeq++)
                    .ckbContent(new ServiceMediaFileContent())
                    .kmrContent(new ServiceMediaFileContent())
                    .build();
            col.addFile(file);
        }
        return col;
    }

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

    // =========================================================================
    // PRIVATE — URL Collectors (orphan cleanup)
    // =========================================================================

    private List<String> collectAllMediaUrls(ak.dev.khi_backend.khi_app.model.service.Service service) {
        return service.getMediaCollections().stream()
                .flatMap(col -> col.getFiles().stream())
                .flatMap(f -> {
                    List<String> urls = new ArrayList<>();
                    if (f.getFileUrl() != null)     urls.add(f.getFileUrl());
                    if (f.getThumbnailUrl() != null) urls.add(f.getThumbnailUrl());
                    return urls.stream();
                })
                .collect(Collectors.toList());
    }

    private List<String> collectAllMediaUrlsFromRequest(ServiceRequest request) {
        if (request.getMediaCollections() == null) return new ArrayList<>();
        return request.getMediaCollections().stream()
                .filter(c -> c.getFiles() != null)
                .flatMap(c -> c.getFiles().stream())
                .flatMap(f -> {
                    List<String> urls = new ArrayList<>();
                    if (f.getFileUrl() != null)     urls.add(f.getFileUrl());
                    if (f.getThumbnailUrl() != null) urls.add(f.getThumbnailUrl());
                    return urls.stream();
                })
                .collect(Collectors.toList());
    }

    // =========================================================================
    // PRIVATE — Validation
    // =========================================================================

    private void validateContents(List<ServiceContentRequest> contents) {
        if (contents == null || contents.isEmpty()) return;

        long distinctCodes = contents.stream()
                .map(c -> c.getLanguageCode() == null ? "" : c.getLanguageCode().toUpperCase())
                .distinct().count();

        if (distinctCodes < contents.size()) {
            throw new BadRequestException("service.content.duplicate_language",
                    Map.of("message", "Duplicate language codes in contents list"));
        }

        for (ServiceContentRequest cr : contents) {
            if (cr.getLanguageCode() == null || cr.getLanguageCode().isBlank()) {
                throw new BadRequestException("service.content.language.required",
                        Map.of("field", "languageCode"));
            }
            String code = cr.getLanguageCode().toUpperCase();
            if (!ALLOWED_LANG_CODES.contains(code)) {
                throw new BadRequestException("service.content.language.unsupported",
                        Map.of("languageCode", cr.getLanguageCode(),
                                "allowed", ALLOWED_LANG_CODES));
            }
            if (cr.getTitle() == null || cr.getTitle().isBlank()) {
                throw new BadRequestException("service.content.title.required",
                        Map.of("languageCode", cr.getLanguageCode()));
            }
        }
    }

    // =========================================================================
    // PRIVATE — Entity Builders
    // =========================================================================

    private ServiceContent buildContent(ServiceContentRequest req) {
        return ServiceContent.builder()
                .languageCode(req.getLanguageCode().toUpperCase().trim())
                .title(req.getTitle().trim())
                .description(tiptapHtmlProcessor.process(req.getDescription()))
                .build();
    }

    private ServiceMediaCollection buildCollection(ServiceMediaCollectionRequest req, int defaultOrder) {
        ServiceMediaCollection col = ServiceMediaCollection.builder()
                .collectionName(trimRequired(req.getCollectionName(), "collectionName"))
                .mediaType(resolveMediaTypeWithFallback(req.getMediaType(), req.getFiles()))
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
        ServiceMediaCollection.MediaType type = resolveMediaType(req.getMediaType());
        if (type == null) {
            throw new BadRequestException("service.collection.mediaType.required",
                    Map.of("field", "mediaType"));
        }
        return ServiceMediaCollection.builder()
                .collectionName(trimRequired(req.getCollectionName(), "collectionName"))
                .mediaType(type)
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : defaultOrder)
                .build();
    }

    private ServiceMediaFile buildFileFromRequest(ServiceMediaFileRequest req, int defaultOrder) {
        if (req.getFileUrl() == null || req.getFileUrl().isBlank()) {
            throw new BadRequestException("service.file.url.required",
                    Map.of("field", "fileUrl"));
        }
        return ServiceMediaFile.builder()
                .fileUrl(req.getFileUrl().trim())
                .thumbnailUrl(trimOrNull(req.getThumbnailUrl()))
                .ckbContent(buildFileContent(req.getCkbContent()))
                .kmrContent(buildFileContent(req.getKmrContent()))
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : defaultOrder)
                .build();
    }

    private ServiceMediaFileContent buildFileContent(FileContentRequest req) {
        if (req == null) return new ServiceMediaFileContent();
        return ServiceMediaFileContent.builder()
                .caption(req.getCaption())
                .title(req.getTitle())
                .description(tiptapHtmlProcessor.process(req.getDescription()))
                .build();
    }

    // =========================================================================
    // PRIVATE — Type Resolvers
    // =========================================================================

    private ServiceMediaCollection.MediaType resolveMediaType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;   // caller decides the fallback
        }
        try {
            return ServiceMediaCollection.MediaType.valueOf(raw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("service.collection.mediaType.invalid",
                    Map.of("mediaType", raw, "allowed", "IMAGE, VIDEO, AUDIO"));
        }
    }

    /**
     * Resolve collection media type: explicit value → infer from file URLs → default IMAGE.
     */
    private ServiceMediaCollection.MediaType resolveMediaTypeWithFallback(
            String raw, List<ServiceMediaFileRequest> files) {

        // 1. Explicit value — use it
        ServiceMediaCollection.MediaType explicit = resolveMediaType(raw);
        if (explicit != null) return explicit;

        // 2. Infer from the first file URL extension
        if (files != null && !files.isEmpty()) {
            for (ServiceMediaFileRequest f : files) {
                ServiceMediaCollection.MediaType guessed = guessMediaTypeFromUrl(f.getFileUrl());
                if (guessed != null) return guessed;
            }
        }

        // 3. Default
        return ServiceMediaCollection.MediaType.IMAGE;
    }

    /**
     * Guess IMAGE / VIDEO / AUDIO from a file URL extension.
     */
    private ServiceMediaCollection.MediaType guessMediaTypeFromUrl(String url) {
        if (url == null) return null;
        String lower = url.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".svg")
                || lower.endsWith(".bmp") || lower.endsWith(".tiff")) {
            return ServiceMediaCollection.MediaType.IMAGE;
        }
        if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi")
                || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".wmv")
                || lower.endsWith(".flv")) {
            return ServiceMediaCollection.MediaType.VIDEO;
        }
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac")
                || lower.endsWith(".flac") || lower.endsWith(".ogg") || lower.endsWith(".wma")
                || lower.endsWith(".m4a")) {
            return ServiceMediaCollection.MediaType.AUDIO;
        }
        return null;
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

    // =========================================================================
    // PRIVATE — Formatting
    // =========================================================================

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw, FORMATTER);
        } catch (Exception e) {
            throw new BadRequestException("service.publishedAt.invalid",
                    Map.of("expected", "yyyy-MM-dd HH:mm:ss", "got", raw));
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
            throw new BadRequestException("service.field.required",
                    Map.of("field", field));
        }
        return value.trim();
    }

    private String trimOrNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // =========================================================================
    // PRIVATE — Audit Logging
    // =========================================================================

    private void auditLog(ak.dev.khi_backend.khi_app.model.service.Service service,
                          String action, String details, String traceId) {
        auditLogRepository.save(buildAuditLog(service, action, details, traceId));
    }

    private ServiceAuditLog buildAuditLog(ak.dev.khi_backend.khi_app.model.service.Service service,
                                          String action, String details, String traceId) {
        return ServiceAuditLog.builder()
                .serviceId(service.getId())
                .serviceType(service.getServiceType())
                .action(action)
                .details(details)
                .performedBy("system")
                .requestId(traceId)
                .build();
    }

    // =========================================================================
    // PRIVATE — Trace ID
    // =========================================================================

    private String traceId() {
        String id = MDC.get("traceId");
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("traceId", id);
        }
        return id;
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
                        .sorted(Comparator.comparingInt(ServiceMediaCollection::getSortOrder))
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
                        .sorted(Comparator.comparingInt(ServiceMediaFile::getSortOrder))
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

