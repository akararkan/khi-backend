package ak.dev.khi_backend.khi_app.service.publishment.writing;


import ak.dev.khi_backend.khi_app.dto.publishment.writing.WritingDtos.*;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.model.publishment.writing.Writing;
import ak.dev.khi_backend.khi_app.model.publishment.writing.WritingContent;
import ak.dev.khi_backend.khi_app.model.publishment.writing.WritingLog;
import ak.dev.khi_backend.khi_app.repository.publishment.writing.WritingLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.writing.WritingRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WritingService {

    private final WritingRepository writingRepository;
    private final WritingLogRepository writingLogRepository;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;

    // ============================================================
    // CREATE - Add new Writing/Book
    // ============================================================

    @Transactional
    public Response addWriting(CreateRequest request,
                               MultipartFile ckbCoverImage,
                               MultipartFile kmrCoverImage,
                               MultipartFile ckbBookFile,
                               MultipartFile kmrBookFile) {

        log.info("Adding bilingual Writing: {}", getCombinedTitle(request));

        // ✅ Validate
        validate(request, true);

        // Upload files to S3
        String ckbCoverUrl = uploadFile(ckbCoverImage, "CKB cover");
        String kmrCoverUrl = uploadFile(kmrCoverImage, "KMR cover");
        String ckbFileUrl = uploadFile(ckbBookFile, "CKB book file");
        String kmrFileUrl = uploadFile(kmrBookFile, "KMR book file");

        // ✅ Build Writing entity
        Writing writing = Writing.builder()
                .contentLanguages(new LinkedHashSet<>(safeLangs(request.getContentLanguages())))
                .writingTopic(request.getWritingTopic())
                .publishedByInstitute(request.isPublishedByInstitute())
                .tagsCkb(new LinkedHashSet<>(safeSet(request.getTags() != null ? request.getTags().getCkb() : null)))
                .tagsKmr(new LinkedHashSet<>(safeSet(request.getTags() != null ? request.getTags().getKmr() : null)))
                .keywordsCkb(new LinkedHashSet<>(safeSet(request.getKeywords() != null ? request.getKeywords().getCkb() : null)))
                .keywordsKmr(new LinkedHashSet<>(safeSet(request.getKeywords() != null ? request.getKeywords().getKmr() : null)))
                .build();

        // ✅ Apply content by languages
        applyContentByLanguages(writing, request, ckbCoverUrl, kmrCoverUrl, ckbFileUrl, kmrFileUrl);

        // Save
        Writing saved = writingRepository.save(writing);

        // Log
        logAction(saved, "CREATED", String.format("Writing '%s' created", getCombinedTitle(saved)));

        log.info("Writing created successfully with ID: {}", saved.getId());
        return mapToResponse(saved);
    }

    // ============================================================
    // GET ALL (with pagination)
    // ============================================================

    @Transactional(readOnly = true)
    public Page<Response> getAllWritings(Pageable pageable) {
        log.info("Fetching all Writings with pagination: page={}, size={}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<Writing> writings = writingRepository.findAll(pageable);

        return writings.map(this::mapToResponse);
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @Transactional
    public Response updateWriting(Long id,
                                  UpdateRequest request,
                                  MultipartFile ckbCoverImage,
                                  MultipartFile kmrCoverImage,
                                  MultipartFile ckbBookFile,
                                  MultipartFile kmrBookFile) {

        log.info("Updating Writing with ID: {}", id);

        Writing writing = writingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Writing not found with id: " + id));

        // ✅ Validate
        validate(request);

        Map<String, Object> changes = new HashMap<>();

        // Upload new files if provided
        String ckbCoverUrl = uploadFile(ckbCoverImage, "CKB cover");
        String kmrCoverUrl = uploadFile(kmrCoverImage, "KMR cover");
        String ckbFileUrl = uploadFile(ckbBookFile, "CKB book file");
        String kmrFileUrl = uploadFile(kmrBookFile, "KMR book file");

        if (request.getWritingTopic() != null && request.getWritingTopic() != writing.getWritingTopic()) {
            changes.put("writingTopic", Map.of("old", writing.getWritingTopic(), "new", request.getWritingTopic()));
            writing.setWritingTopic(request.getWritingTopic());
        }

        if (request.getPublishedByInstitute() != null) {
            writing.setPublishedByInstitute(request.getPublishedByInstitute());
        }

        // ✅ Update languages and content
        if (request.getContentLanguages() != null) {
            writing.setContentLanguages(new LinkedHashSet<>(request.getContentLanguages()));
        }
        applyContentByLanguages(writing, request, ckbCoverUrl, kmrCoverUrl, ckbFileUrl, kmrFileUrl);

        // ✅ Update bilingual tags/keywords
        replaceBilingualSets(writing, request);

        Writing updated = writingRepository.save(writing);

        if (!changes.isEmpty() || ckbCoverUrl != null || kmrCoverUrl != null || ckbFileUrl != null || kmrFileUrl != null) {
            try {
                changes.put("filesUpdated", true);
                String changesJson = objectMapper.writeValueAsString(changes);
                logAction(updated, "UPDATED", changesJson);
            } catch (Exception e) {
                log.error("Failed to serialize changes", e);
                logAction(updated, "UPDATED", "Writing updated");
            }
        }

        return mapToResponse(updated);
    }

    // ============================================================
    // DELETE
    // ============================================================

    @Transactional
    public void deleteWriting(Long id) {
        log.info("Deleting Writing with ID: {}", id);

        Writing writing = writingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Writing not found with id: " + id));

        String title = getCombinedTitle(writing);
        logAction(writing, "DELETED", String.format("Writing '%s' deleted", title));
        writingRepository.delete(writing);
    }

    // ============================================================
    // SEARCH BY TAG (with pagination)
    // ============================================================

    @Transactional(readOnly = true)
    public Page<Response> searchByTag(String tag, String language, Pageable pageable) {
        if (isBlank(tag)) {
            log.warn("Empty tag provided for search");
            return Page.empty(pageable);
        }

        String cleanTag = tag.trim();
        log.info("Searching writings by tag: '{}' in language: {} with pagination", cleanTag, language);

        List<Writing> allResults = new ArrayList<>();

        if ("ckb".equalsIgnoreCase(language)) {
            allResults.addAll(writingRepository.findByTagCkb(cleanTag));
        } else if ("kmr".equalsIgnoreCase(language)) {
            allResults.addAll(writingRepository.findByTagKmr(cleanTag));
        } else {
            // Search in both languages (use Set to avoid duplicates)
            Set<Writing> uniqueResults = new LinkedHashSet<>();
            uniqueResults.addAll(writingRepository.findByTagCkb(cleanTag));
            uniqueResults.addAll(writingRepository.findByTagKmr(cleanTag));
            allResults.addAll(uniqueResults);
        }

        log.info("Found {} writings with tag '{}'", allResults.size(), cleanTag);
        return convertListToPage(allResults, pageable);
    }

    // ============================================================
    // SEARCH BY KEYWORD (with pagination)
    // ============================================================

    @Transactional(readOnly = true)
    public Page<Response> searchByKeyword(String keyword, String language, Pageable pageable) {
        if (isBlank(keyword)) {
            log.warn("Empty keyword provided for search");
            return Page.empty(pageable);
        }

        String cleanKeyword = keyword.trim();
        log.info("Searching writings by keyword: '{}' in language: {} with pagination", cleanKeyword, language);

        List<Writing> allResults = new ArrayList<>();

        if ("ckb".equalsIgnoreCase(language)) {
            allResults.addAll(writingRepository.findByKeywordCkb(cleanKeyword));
        } else if ("kmr".equalsIgnoreCase(language)) {
            allResults.addAll(writingRepository.findByKeywordKmr(cleanKeyword));
        } else {
            Set<Writing> uniqueResults = new LinkedHashSet<>();
            uniqueResults.addAll(writingRepository.findByKeywordCkb(cleanKeyword));
            uniqueResults.addAll(writingRepository.findByKeywordKmr(cleanKeyword));
            allResults.addAll(uniqueResults);
        }

        log.info("Found {} writings with keyword '{}'", allResults.size(), cleanKeyword);
        return convertListToPage(allResults, pageable);
    }

    // ============================================================
    // PAGINATION HELPER
    // ============================================================

    private Page<Response> convertListToPage(List<Writing> writings, Pageable pageable) {
        // Sort the list based on pageable sort (if any)
        if (pageable.getSort().isSorted()) {
            writings = sortWritings(writings, pageable.getSort());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), writings.size());

        List<Response> pageContent;
        if (start >= writings.size()) {
            pageContent = Collections.emptyList();
        } else {
            pageContent = writings.subList(start, end).stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }

        return new PageImpl<>(pageContent, pageable, writings.size());
    }

    private List<Writing> sortWritings(List<Writing> writings, org.springframework.data.domain.Sort sort) {
        Comparator<Writing> comparator = null;

        for (org.springframework.data.domain.Sort.Order order : sort) {
            Comparator<Writing> currentComparator = null;

            switch (order.getProperty()) {
                case "createdAt":
                    currentComparator = Comparator.comparing(Writing::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
                    break;
                case "updatedAt":
                    currentComparator = Comparator.comparing(Writing::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
                    break;
                case "id":
                    currentComparator = Comparator.comparing(Writing::getId, Comparator.nullsLast(Comparator.naturalOrder()));
                    break;
                default:
                    currentComparator = Comparator.comparing(Writing::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            }

            if (order.isDescending()) {
                currentComparator = currentComparator.reversed();
            }

            comparator = (comparator == null) ? currentComparator : comparator.thenComparing(currentComparator);
        }

        if (comparator != null) {
            writings.sort(comparator);
        }

        return writings;
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private void validate(CreateRequest request, boolean isCreate) {
        if (request == null) throw new IllegalArgumentException("Request cannot be null");

        Set<Language> langs = safeLangs(request.getContentLanguages());
        if (langs.isEmpty()) {
            throw new IllegalArgumentException("At least one content language is required");
        }

        if (request.getWritingTopic() == null) {
            throw new IllegalArgumentException("Writing topic is required");
        }

        // Validate content for active languages
        if (langs.contains(Language.CKB)) {
            if (request.getCkbContent() == null || isBlank(request.getCkbContent().getTitle())) {
                throw new IllegalArgumentException("CKB title is required when CKB language is active");
            }
        }

        if (langs.contains(Language.KMR)) {
            if (request.getKmrContent() == null || isBlank(request.getKmrContent().getTitle())) {
                throw new IllegalArgumentException("KMR title is required when KMR language is active");
            }
        }
    }

    private void validate(UpdateRequest request) {
        if (request == null) throw new IllegalArgumentException("Request cannot be null");

        if (request.getContentLanguages() != null && !request.getContentLanguages().isEmpty()) {
            Set<Language> langs = request.getContentLanguages();

            if (langs.contains(Language.CKB)) {
                if (request.getCkbContent() != null && isBlank(request.getCkbContent().getTitle())) {
                    throw new IllegalArgumentException("CKB title cannot be empty when CKB language is active");
                }
            }

            if (langs.contains(Language.KMR)) {
                if (request.getKmrContent() != null && isBlank(request.getKmrContent().getTitle())) {
                    throw new IllegalArgumentException("KMR title cannot be empty when KMR language is active");
                }
            }
        }
    }

    // ============================================================
    // BILINGUAL LOGIC
    // ============================================================

    private void applyContentByLanguages(Writing writing, CreateRequest request,
                                         String ckbCoverUrl, String kmrCoverUrl,
                                         String ckbFileUrl, String kmrFileUrl) {
        Set<Language> langs = safeLangs(writing.getContentLanguages());

        if (langs.contains(Language.CKB)) {
            writing.setCkbContent(buildContent(request.getCkbContent(), ckbCoverUrl, ckbFileUrl));
        } else {
            writing.setCkbContent(null);
            writing.getTagsCkb().clear();
            writing.getKeywordsCkb().clear();
        }

        if (langs.contains(Language.KMR)) {
            writing.setKmrContent(buildContent(request.getKmrContent(), kmrCoverUrl, kmrFileUrl));
        } else {
            writing.setKmrContent(null);
            writing.getTagsKmr().clear();
            writing.getKeywordsKmr().clear();
        }
    }

    private void applyContentByLanguages(Writing writing, UpdateRequest request,
                                         String ckbCoverUrl, String kmrCoverUrl,
                                         String ckbFileUrl, String kmrFileUrl) {
        Set<Language> langs = safeLangs(writing.getContentLanguages());

        if (langs.contains(Language.CKB)) {
            if (request.getCkbContent() != null) {
                WritingContent existing = writing.getCkbContent();
                writing.setCkbContent(updateContent(existing, request.getCkbContent(), ckbCoverUrl, ckbFileUrl));
            }
        } else {
            writing.setCkbContent(null);
            writing.getTagsCkb().clear();
            writing.getKeywordsCkb().clear();
        }

        if (langs.contains(Language.KMR)) {
            if (request.getKmrContent() != null) {
                WritingContent existing = writing.getKmrContent();
                writing.setKmrContent(updateContent(existing, request.getKmrContent(), kmrCoverUrl, kmrFileUrl));
            }
        } else {
            writing.setKmrContent(null);
            writing.getTagsKmr().clear();
            writing.getKeywordsKmr().clear();
        }
    }

    private WritingContent buildContent(LanguageContentDto dto, String coverUrl, String fileUrl) {
        if (dto == null) return null;
        if (isBlank(dto.getTitle())) return null;

        return WritingContent.builder()
                .title(trimOrNull(dto.getTitle()))
                .description(trimOrNull(dto.getDescription()))
                .writer(trimOrNull(dto.getWriter()))
                .coverUrl(coverUrl != null ? coverUrl : trimOrNull(dto.getCoverUrl()))
                .fileUrl(fileUrl != null ? fileUrl : trimOrNull(dto.getFileUrl()))
                .fileFormat(dto.getFileFormat())
                .fileSizeBytes(dto.getFileSizeBytes())
                .pageCount(dto.getPageCount())
                .genre(trimOrNull(dto.getGenre()))
                .build();
    }

    private WritingContent updateContent(WritingContent existing, LanguageContentDto dto,
                                         String coverUrl, String fileUrl) {
        if (existing == null) {
            return buildContent(dto, coverUrl, fileUrl);
        }

        if (dto.getTitle() != null) existing.setTitle(trimOrNull(dto.getTitle()));
        if (dto.getDescription() != null) existing.setDescription(trimOrNull(dto.getDescription()));
        if (dto.getWriter() != null) existing.setWriter(trimOrNull(dto.getWriter()));
        if (coverUrl != null) existing.setCoverUrl(coverUrl);
        else if (dto.getCoverUrl() != null) existing.setCoverUrl(trimOrNull(dto.getCoverUrl()));
        if (fileUrl != null) existing.setFileUrl(fileUrl);
        else if (dto.getFileUrl() != null) existing.setFileUrl(trimOrNull(dto.getFileUrl()));
        if (dto.getFileFormat() != null) existing.setFileFormat(dto.getFileFormat());
        if (dto.getFileSizeBytes() != null) existing.setFileSizeBytes(dto.getFileSizeBytes());
        if (dto.getPageCount() != null) existing.setPageCount(dto.getPageCount());
        if (dto.getGenre() != null) existing.setGenre(trimOrNull(dto.getGenre()));

        return existing;
    }

    private void replaceBilingualSets(Writing writing, UpdateRequest request) {
        if (request.getTags() != null) {
            if (request.getTags().getCkb() != null) {
                writing.getTagsCkb().clear();
                writing.getTagsCkb().addAll(cleanStrings(request.getTags().getCkb()));
            }
            if (request.getTags().getKmr() != null) {
                writing.getTagsKmr().clear();
                writing.getTagsKmr().addAll(cleanStrings(request.getTags().getKmr()));
            }
        }

        if (request.getKeywords() != null) {
            if (request.getKeywords().getCkb() != null) {
                writing.getKeywordsCkb().clear();
                writing.getKeywordsCkb().addAll(cleanStrings(request.getKeywords().getCkb()));
            }
            if (request.getKeywords().getKmr() != null) {
                writing.getKeywordsKmr().clear();
                writing.getKeywordsKmr().addAll(cleanStrings(request.getKeywords().getKmr()));
            }
        }
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private String uploadFile(MultipartFile file, String description) {
        if (file == null || file.isEmpty()) return null;

        try {
            String url = s3Service.upload(file.getBytes(), file.getOriginalFilename(), file.getContentType());
            log.info("{} uploaded to S3: {}", description, url);
            return url;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload " + description, e);
        }
    }

    private Response mapToResponse(Writing writing) {
        Response response = Response.builder()
                .id(writing.getId())
                .contentLanguages(writing.getContentLanguages() != null ?
                        new LinkedHashSet<>(writing.getContentLanguages()) : new LinkedHashSet<>())
                .writingTopic(writing.getWritingTopic())
                .publishedByInstitute(writing.isPublishedByInstitute())
                .createdAt(writing.getCreatedAt())
                .updatedAt(writing.getUpdatedAt())
                .build();

        // CKB Content
        if (writing.getCkbContent() != null) {
            response.setCkbContent(LanguageContentDto.builder()
                    .title(writing.getCkbContent().getTitle())
                    .description(writing.getCkbContent().getDescription())
                    .writer(writing.getCkbContent().getWriter())
                    .coverUrl(writing.getCkbContent().getCoverUrl())
                    .fileUrl(writing.getCkbContent().getFileUrl())
                    .fileFormat(writing.getCkbContent().getFileFormat())
                    .fileSizeBytes(writing.getCkbContent().getFileSizeBytes())
                    .pageCount(writing.getCkbContent().getPageCount())
                    .genre(writing.getCkbContent().getGenre())
                    .build());
        }

        // KMR Content
        if (writing.getKmrContent() != null) {
            response.setKmrContent(LanguageContentDto.builder()
                    .title(writing.getKmrContent().getTitle())
                    .description(writing.getKmrContent().getDescription())
                    .writer(writing.getKmrContent().getWriter())
                    .coverUrl(writing.getKmrContent().getCoverUrl())
                    .fileUrl(writing.getKmrContent().getFileUrl())
                    .fileFormat(writing.getKmrContent().getFileFormat())
                    .fileSizeBytes(writing.getKmrContent().getFileSizeBytes())
                    .pageCount(writing.getKmrContent().getPageCount())
                    .genre(writing.getKmrContent().getGenre())
                    .build());
        }

        // Tags
        response.setTags(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(writing.getTagsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(writing.getTagsKmr())))
                .build());

        // Keywords
        response.setKeywords(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(writing.getKeywordsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(writing.getKeywordsKmr())))
                .build());

        return response;
    }

    private void logAction(Writing writing, String action, String details) {
        WritingLog logEntry = WritingLog.builder()
                .writing(writing)
                .action(action)
                .actorId("system")
                .actorName("System")
                .details(details)
                .createdAt(LocalDateTime.now())
                .build();

        writingLogRepository.save(logEntry);
    }

    private String getCombinedTitle(Writing writing) {
        if (writing.getCkbContent() != null && !isBlank(writing.getCkbContent().getTitle())) {
            return writing.getCkbContent().getTitle();
        }
        if (writing.getKmrContent() != null && !isBlank(writing.getKmrContent().getTitle())) {
            return writing.getKmrContent().getTitle();
        }
        return "Unknown";
    }

    private String getCombinedTitle(CreateRequest request) {
        if (request.getCkbContent() != null && !isBlank(request.getCkbContent().getTitle())) {
            return request.getCkbContent().getTitle();
        }
        if (request.getKmrContent() != null && !isBlank(request.getKmrContent().getTitle())) {
            return request.getKmrContent().getTitle();
        }
        return "Unknown";
    }

    // ============================================================
    // UTILS
    // ============================================================

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Set<Language> safeLangs(Set<Language> langs) {
        return langs == null ? Set.of() : langs;
    }

    private <T> Set<T> safeSet(Set<T> s) {
        return s == null ? Set.of() : s;
    }

    private Set<String> cleanStrings(Set<String> input) {
        if (input == null || input.isEmpty()) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : input) {
            if (s != null && !s.isBlank()) out.add(s.trim());
        }
        return out;
    }
}