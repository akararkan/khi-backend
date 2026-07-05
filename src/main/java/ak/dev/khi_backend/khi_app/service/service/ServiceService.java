package ak.dev.khi_backend.khi_app.service.service;

import ak.dev.khi_backend.khi_app.dto.service.ServiceDTOs.*;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.exceptions.NotFoundException;
import ak.dev.khi_backend.khi_app.model.service.ServiceAuditLog;
import ak.dev.khi_backend.khi_app.model.service.ServiceContent;
import ak.dev.khi_backend.khi_app.repository.service.ServiceAuditLogRepository;
import ak.dev.khi_backend.khi_app.repository.service.ServiceRepository;
import ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ServiceService — Business logic for the Service module.
 *
 * <p>Service no longer manages a separate media-file model.  All media
 * (image / video / voice / document / other) is embedded inside the
 * bilingual Tiptap {@code description} on each {@link ServiceContent}
 * row — uploaded once via the shared {@code POST /api/v1/media/upload}
 * and rewritten to S3 by
 * {@link ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor}.</p>
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Full CRUD for {@link ak.dev.khi_backend.khi_app.model.service.Service}.</li>
 *   <li>Bilingual content management (CKB / KMR rows).</li>
 *   <li>Two-phase pagination (ID scan → batch hydration) to avoid N+1.</li>
 *   <li>Redis caching on read paths with eviction on every write.</li>
 *   <li>Audit logging for all CUD operations.</li>
 *   <li>Global search across service type, location, and bilingual content.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceService {

    private static final Set<String>    ALLOWED_LANG_CODES = Set.of("CKB", "KMR");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ServiceRepository         serviceRepository;
    private final ServiceAuditLogRepository auditLogRepository;
    private final TiptapHtmlProcessor       tiptapHtmlProcessor;

    // =========================================================================
    // READ — Paginated + Cached (Two-Phase Hydration)
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

    /**
     * Services do not participate in the curated featured-content model.
     * Keep the dashboard's generic per-resource endpoint stable by returning
     * an empty page instead of routing the literal "featured" to /{id}.
     */
    public Page<ServiceResponse> getFeatured(int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)
        );
        return Page.empty(pageable);
    }

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

    @Transactional(readOnly = true)
    public ServiceResponse getById(Long id) {
        ak.dev.khi_backend.khi_app.model.service.Service service =
                serviceRepository.findByIdWithAll(id)
                        .orElseThrow(() -> new NotFoundException(
                                "service.not_found", Map.of("id", id)));
        return toResponse(service);
    }

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
                        .active(true)
                        .publishedAt(parseDateTime(request.getPublishedAt()))
                        .layoutType(trimOrNull(request.getLayoutType()))
                        .heroVideoUrl(trimOrNull(request.getHeroVideoUrl()))
                        .heroPosterUrl(trimOrNull(request.getHeroPosterUrl()))
                        .navAnchorId(trimOrNull(request.getNavAnchorId()))
                        .featureImageUrls(cleanStrings(request.getFeatureImageUrls()))
                        .thumbnailUrls(cleanStrings(request.getThumbnailUrls()))
                        .partnerIds(cleanIds(request.getPartnerIds()))
                        .build();

        if (request.getContents() != null) {
            for (ServiceContentRequest cr : request.getContents()) {
                service.addContent(buildContent(cr));
            }
        }

        ak.dev.khi_backend.khi_app.model.service.Service saved =
                serviceRepository.save(service);

        auditLog(saved, "CREATE", "Service created: " + saved.getServiceType(), traceId);
        log.info("Service created | id={} | traceId={}", saved.getId(), traceId);

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

        service.setServiceType(trimRequired(request.getServiceType(), "serviceType"));
        service.setLocation(trimOrNull(request.getLocation()));
        service.setPublishedAt(parseDateTime(request.getPublishedAt()));
        service.setLayoutType(trimOrNull(request.getLayoutType()));
        service.setHeroVideoUrl(trimOrNull(request.getHeroVideoUrl()));
        service.setHeroPosterUrl(trimOrNull(request.getHeroPosterUrl()));
        service.setNavAnchorId(trimOrNull(request.getNavAnchorId()));
        service.getFeatureImageUrls().clear();
        service.getFeatureImageUrls().addAll(cleanStrings(request.getFeatureImageUrls()));
        service.getThumbnailUrls().clear();
        service.getThumbnailUrls().addAll(cleanStrings(request.getThumbnailUrls()));
        service.getPartnerIds().clear();
        service.getPartnerIds().addAll(cleanIds(request.getPartnerIds()));

        // Clear first, then flush to force DELETEs before INSERTs
        // (prevents unique-constraint violations on re-insert)
        service.getContents().clear();
        serviceRepository.saveAndFlush(service);

        if (request.getContents() != null) {
            for (ServiceContentRequest cr : request.getContents()) {
                service.addContent(buildContent(cr));
            }
        }

        ak.dev.khi_backend.khi_app.model.service.Service saved =
                serviceRepository.save(service);

        auditLog(saved, "UPDATE", "Service updated: " + saved.getServiceType(), traceId);
        log.info("Service updated | id={} | traceId={}", saved.getId(), traceId);

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

        auditLog(service, "DELETE",
                "Service deleted: " + service.getServiceType(), traceId);

        serviceRepository.delete(service);

        log.info("Service deleted | id={} | traceId={}", id, traceId);
    }

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public void deleteBulk(List<Long> ids) {
        String traceId = traceId();
        log.info("Bulk deleting services | count={} | traceId={}",
                ids != null ? ids.size() : 0, traceId);

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

        auditLogRepository.saveAll(
                services.stream()
                        .map(s -> buildAuditLog(s, "DELETE",
                                "Service bulk-deleted: " + s.getServiceType(), traceId))
                        .toList()
        );

        serviceRepository.deleteAll(services);

        log.info("Bulk delete complete | deleted={} | traceId={}",
                services.size(), traceId);
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

    // =========================================================================
    // PRIVATE — Formatting
    // =========================================================================

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw, FORMATTER);
        } catch (Exception e1) {
            try {
                // Accept ISO 8601 (e.g. "2026-06-30T21:01:42.260Z")
                return java.time.OffsetDateTime.parse(raw).toLocalDateTime();
            } catch (Exception e2) {
                throw new BadRequestException("service.publishedAt.invalid",
                        Map.of("expected", "yyyy-MM-dd HH:mm:ss or ISO-8601", "got", raw));
            }
        }
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

    private List<String> cleanStrings(List<String> values) {
        if (values == null) return new ArrayList<>();
        return values.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty()).distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Long> cleanIds(List<Long> values) {
        if (values == null) return new ArrayList<>();
        return values.stream().filter(Objects::nonNull).distinct()
                .collect(Collectors.toCollection(ArrayList::new));
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
                .active(service.isActive())
                .publishedAt(service.getPublishedAt() != null
                        ? service.getPublishedAt().format(FORMATTER) : null)
                .layoutType(service.getLayoutType())
                .heroVideoUrl(service.getHeroVideoUrl())
                .heroPosterUrl(service.getHeroPosterUrl())
                .navAnchorId(service.getNavAnchorId())
                .featureImageUrls(new ArrayList<>(service.getFeatureImageUrls()))
                .thumbnailUrls(new ArrayList<>(service.getThumbnailUrls()))
                .partnerIds(new ArrayList<>(service.getPartnerIds()))
                .contents(service.getContents().stream()
                        .map(this::toContentResponse)
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
}
