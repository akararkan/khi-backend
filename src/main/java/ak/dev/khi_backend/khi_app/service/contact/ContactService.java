package ak.dev.khi_backend.khi_app.service.contact;

import ak.dev.khi_backend.khi_app.dto.contact.ContactDTOs.*;
import ak.dev.khi_backend.khi_app.model.contact.Contact;
import ak.dev.khi_backend.khi_app.model.contact.ContactContent;
import ak.dev.khi_backend.khi_app.repository.contact.ContactRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final S3Service         s3Service;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ============================================================
    // READ
    // ============================================================

    @Transactional(readOnly = true)
    public List<ContactResponse> getAll() {
        return contactRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> getAllActive() {
        return contactRepository.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lookup by either the CKB slug or the KMR slug.
     */
    @Transactional(readOnly = true)
    public ContactResponse getBySlug(String slug) {
        Contact contact = contactRepository.findBySlugCkbOrSlugKmr(slug, slug)
                .orElseThrow(() ->
                        new EntityNotFoundException("Contact page not found: " + slug));
        return toResponse(contact);
    }

    @Transactional(readOnly = true)
    public ContactResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Transactional
    public ContactResponse create(ContactRequest request) {

        validateSlugs(request, null);

        Contact contact = Contact.builder()
                .slugCkb(request.getSlugCkb().trim())
                .slugKmr(blankToNull(request.getSlugKmr()))
                .heroImageUrl(blankToNull(request.getHeroImageUrl()))
                .ckbContent(buildContent(request.getCkbContent()))
                .kmrContent(buildContent(request.getKmrContent()))
                .phone(blankToNull(request.getPhone()))
                .secondaryPhone(blankToNull(request.getSecondaryPhone()))
                .email(blankToNull(request.getEmail()))
                .mapEmbedUrl(blankToNull(request.getMapEmbedUrl()))
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .active(true)
                .build();

        Contact saved = contactRepository.save(contact);
        log.info("Contact page created — id={}, slugCkb={}", saved.getId(), saved.getSlugCkb());
        return toResponse(saved);
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @Transactional
    public ContactResponse update(Long id, ContactRequest request) {

        Contact contact = findOrThrow(id);
        validateSlugs(request, id);

        // Delete old hero from S3 if it changed
        String oldHero = contact.getHeroImageUrl();
        String newHero = blankToNull(request.getHeroImageUrl());
        if (oldHero != null && !oldHero.equals(newHero)) {
            s3Service.deleteFile(oldHero);
            log.info("Deleted old hero image from S3: {}", oldHero);
        }

        contact.setSlugCkb(request.getSlugCkb().trim());
        contact.setSlugKmr(blankToNull(request.getSlugKmr()));
        contact.setHeroImageUrl(newHero);
        contact.setCkbContent(buildContent(request.getCkbContent()));
        contact.setKmrContent(buildContent(request.getKmrContent()));
        contact.setPhone(blankToNull(request.getPhone()));
        contact.setSecondaryPhone(blankToNull(request.getSecondaryPhone()));
        contact.setEmail(blankToNull(request.getEmail()));
        contact.setMapEmbedUrl(blankToNull(request.getMapEmbedUrl()));
        contact.setLatitude(request.getLatitude());
        contact.setLongitude(request.getLongitude());

        Contact updated = contactRepository.save(contact);
        log.info("Contact page updated — id={}", updated.getId());
        return toResponse(updated);
    }

    // ============================================================
    // DELETE
    // ============================================================

    @Transactional
    public void delete(Long id) {
        Contact contact = findOrThrow(id);

        if (contact.getHeroImageUrl() != null) {
            s3Service.deleteFile(contact.getHeroImageUrl());
        }

        contactRepository.delete(contact);
        log.info("Deleted contact page id={}", id);
    }

    // ============================================================
    // MEDIA UPLOAD — S3
    // ============================================================

    public UploadResponse uploadMedia(MultipartFile file) throws IOException {
        log.info("Uploading contact media: name={}, size={}", file.getOriginalFilename(), file.getSize());

        String fileUrl = s3Service.upload(
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType()
        );

        log.info("Upload successful: {}", fileUrl);

        return UploadResponse.builder()
                .fileUrl(fileUrl)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .build();
    }

    @Transactional
    public void deleteMedia(String fileUrl) {
        if (fileUrl != null && !fileUrl.isBlank()) {
            s3Service.deleteFile(fileUrl);
            log.info("Deleted contact media from S3: {}", fileUrl);
        }
    }

    // ============================================================
    // PRIVATE HELPERS
    // ============================================================

    private Contact findOrThrow(Long id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Contact not found: " + id));
    }

    /**
     * Validate slug uniqueness for both CKB and KMR.
     * On update, excludes the current record's own ID from the check.
     */
    private void validateSlugs(ContactRequest request, Long excludeId) {

        if (request.getSlugCkb() == null || request.getSlugCkb().isBlank()) {
            throw new IllegalArgumentException("CKB slug is required");
        }

        String ckb = request.getSlugCkb().trim();
        String kmr = blankToNull(request.getSlugKmr());

        contactRepository.findBySlugCkb(ckb).ifPresent(existing -> {
            if (!existing.getId().equals(excludeId)) {
                throw new IllegalArgumentException("CKB slug already exists: " + ckb);
            }
        });

        if (kmr != null) {
            contactRepository.findBySlugKmr(kmr).ifPresent(existing -> {
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

    private ContactContent buildContent(ContactContentRequest req) {
        if (req == null) return new ContactContent();
        return ContactContent.builder()
                .title(req.getTitle())
                .subtitle(req.getSubtitle())
                .address(req.getAddress())
                .workingHours(req.getWorkingHours())
                .build();
    }

    // ─── Response Mapper ──────────────────────────────────────────────────────

    private ContactResponse toResponse(Contact c) {
        return ContactResponse.builder()
                .id(c.getId())
                .slugCkb(c.getSlugCkb())
                .slugKmr(c.getSlugKmr())
                .heroImageUrl(c.getHeroImageUrl())
                .ckbContent(toContentResponse(c.getCkbContent()))
                .kmrContent(toContentResponse(c.getKmrContent()))
                .phone(c.getPhone())
                .secondaryPhone(c.getSecondaryPhone())
                .email(c.getEmail())
                .mapEmbedUrl(c.getMapEmbedUrl())
                .latitude(c.getLatitude())
                .longitude(c.getLongitude())
                .active(c.isActive())
                .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().format(FORMATTER) : null)
                .updatedAt(c.getUpdatedAt() != null ? c.getUpdatedAt().format(FORMATTER) : null)
                .build();
    }

    private ContactContentResponse toContentResponse(ContactContent content) {
        if (content == null) return null;
        return ContactContentResponse.builder()
                .title(content.getTitle())
                .subtitle(content.getSubtitle())
                .address(content.getAddress())
                .workingHours(content.getWorkingHours())
                .build();
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}