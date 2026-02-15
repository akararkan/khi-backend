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

/**
 * ✅ ENHANCED WritingService with:
 * - Book Series/Edition Support
 * - Writer Search (O(log n) performance)
 * - Optimized queries with proper indexing
 */
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

        // ✅ Handle parent book and series
        Writing parentBook = null;
        String seriesId = request.getSeriesId();
        Double seriesOrder = request.getSeriesOrder();

        if (request.getParentBookId() != null) {
            parentBook = writingRepository.findById(request.getParentBookId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Parent book not found with id: " + request.getParentBookId()));

            // Inherit series info from parent
            seriesId = parentBook.getSeriesId();

            // Auto-calculate next order if not provided
            if (seriesOrder == null) {
                Double maxOrder = writingRepository.findMaxSeriesOrder(seriesId);
                seriesOrder = maxOrder + 1.0;
            }
        }

        // ✅ Build Writing entity
        Writing writing = Writing.builder()
                .contentLanguages(new LinkedHashSet<>(safeLangs(request.getContentLanguages())))
                .writingTopic(request.getWritingTopic())
                .publishedByInstitute(request.isPublishedByInstitute())
                .tagsCkb(new LinkedHashSet<>(safeSet(request.getTags() != null ? request.getTags().getCkb() : null)))
                .tagsKmr(new LinkedHashSet<>(safeSet(request.getTags() != null ? request.getTags().getKmr() : null)))
                .keywordsCkb(new LinkedHashSet<>(safeSet(request.getKeywords() != null ? request.getKeywords().getCkb() : null)))
                .keywordsKmr(new LinkedHashSet<>(safeSet(request.getKeywords() != null ? request.getKeywords().getKmr() : null)))
                // ✅ Series fields
                .seriesId(seriesId)
                .seriesName(request.getSeriesName())
                .seriesOrder(seriesOrder)
                .parentBook(parentBook)
                .build();

        // ✅ Apply content by languages
        applyContentByLanguages(writing, request, ckbCoverUrl, kmrCoverUrl, ckbFileUrl, kmrFileUrl);

        // Save
        Writing saved = writingRepository.save(writing);

        // ✅ Update series count
        updateSeriesCount(saved.getSeriesId());

        // Log
        logAction(saved, "CREATED", String.format("Writing '%s' created", getCombinedTitle(saved)));

        log.info("Writing created successfully with ID: {} (Series: {})", saved.getId(), saved.getSeriesId());
        return mapToResponse(saved);
    }

    // ============================================================
    // ✅ NEW: LINK BOOK TO EXISTING SERIES
    // ============================================================

    @Transactional
    public Response linkBookToSeries(LinkToSeriesRequest request) {
        log.info("Linking book {} to series via parent {}", request.getBookId(), request.getParentBookId());

        Writing book = writingRepository.findById(request.getBookId())
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + request.getBookId()));

        Writing parentBook = writingRepository.findById(request.getParentBookId())
                .orElseThrow(() -> new EntityNotFoundException("Parent book not found with id: " + request.getParentBookId()));

        // Update book's series information
        book.setParentBook(parentBook);
        book.setSeriesId(parentBook.getSeriesId());
        book.setSeriesOrder(request.getSeriesOrder());

        if (request.getSeriesName() != null) {
            book.setSeriesName(request.getSeriesName());
        } else {
            book.setSeriesName(parentBook.getSeriesName());
        }

        Writing updated = writingRepository.save(book);

        // ✅ Update series count
        updateSeriesCount(updated.getSeriesId());

        logAction(updated, "LINKED_TO_SERIES",
                String.format("Book linked to series '%s'", parentBook.getSeriesId()));

        return mapToResponse(updated);
    }

    // ============================================================
    // ✅ NEW: GET ALL BOOKS IN A SERIES
    // ============================================================

    @Transactional(readOnly = true)
    public SeriesResponse getSeriesBooks(String seriesId) {
        log.info("Fetching all books in series: {}", seriesId);

        List<Writing> books = writingRepository.findBySeriesIdOrderBySeriesOrderAsc(seriesId);

        if (books.isEmpty()) {
            throw new EntityNotFoundException("No books found in series: " + seriesId);
        }

        // Get series name from first book
        String seriesName = books.get(0).getEffectiveSeriesName();

        List<SeriesBookSummary> summaries = books.stream()
                .map(book -> SeriesBookSummary.builder()
                        .id(book.getId())
                        .titleCkb(book.getCkbContent() != null ? book.getCkbContent().getTitle() : null)
                        .titleKmr(book.getKmrContent() != null ? book.getKmrContent().getTitle() : null)
                        .seriesOrder(book.getSeriesOrder())
                        .createdAt(book.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return SeriesResponse.builder()
                .seriesId(seriesId)
                .seriesName(seriesName)
                .totalBooks(books.size())
                .books(summaries)
                .build();
    }

    // ============================================================
    // ✅ NEW: GET ALL SERIES (PARENT BOOKS ONLY)
    // ============================================================

    @Transactional(readOnly = true)
    public Page<Response> getAllSeriesParents(Pageable pageable) {
        log.info("Fetching all series parent books");

        Page<Writing> parents = writingRepository.findSeriesParents(pageable);
        return parents.map(this::mapToResponse);
    }

    // ============================================================
    // ✅ NEW: SEARCH BY WRITER NAME
    // ============================================================

    @Transactional(readOnly = true)
    public Page<Response> searchByWriter(String writerName, String language, Pageable pageable) {
        if (isBlank(writerName)) {
            log.warn("Empty writer name provided for search");
            return Page.empty(pageable);
        }

        String cleanWriter = writerName.trim();
        log.info("Searching by writer: '{}' | language: {}", cleanWriter, language);

        Page<Writing> results;

        if ("ckb".equalsIgnoreCase(language)) {
            // Search CKB only - O(log n) with idx_writer_ckb
            results = writingRepository.findByWriterCkbContainingIgnoreCase(cleanWriter, pageable);
        } else if ("kmr".equalsIgnoreCase(language)) {
            // Search KMR only - O(log n) with idx_writer_kmr
            results = writingRepository.findByWriterKmrContainingIgnoreCase(cleanWriter, pageable);
        } else {
            // Search both languages - O(log n) + O(log n)
            results = writingRepository.findByWriterInBothLanguages(cleanWriter, pageable);
        }

        log.info("Found {} books by writer '{}'", results.getTotalElements(), cleanWriter);
        return results.map(this::mapToResponse);
    }

    // ============================================================
    // ✅ NEW: GET ALL BOOKS BY WRITER (NO PAGINATION)
    // ============================================================

    @Transactional(readOnly = true)
    public List<Response> getAllBooksByWriter(String writerName, String language) {
        if (isBlank(writerName)) {
            return List.of();
        }

        String cleanWriter = writerName.trim();
        log.info("Getting all books by writer: '{}' | language: {}", cleanWriter, language);

        List<Writing> results = new ArrayList<>();

        if ("ckb".equalsIgnoreCase(language)) {
            results = writingRepository.findAllByWriterCkbExact(cleanWriter);
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = writingRepository.findAllByWriterKmrExact(cleanWriter);
        } else {
            // Combine both languages
            results.addAll(writingRepository.findAllByWriterCkbExact(cleanWriter));
            results.addAll(writingRepository.findAllByWriterKmrExact(cleanWriter));

            // Remove duplicates by ID
            results = results.stream()
                    .collect(Collectors.toMap(
                            Writing::getId,
                            w -> w,
                            (w1, w2) -> w1
                    ))
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(Writing::getCreatedAt).reversed())
                    .collect(Collectors.toList());
        }

        return results.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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

        // ✅ Update series information
        String oldSeriesId = writing.getSeriesId();

        if (request.getSeriesName() != null) {
            writing.setSeriesName(request.getSeriesName());
        }

        if (request.getSeriesOrder() != null) {
            writing.setSeriesOrder(request.getSeriesOrder());
        }

        if (request.getParentBookId() != null) {
            Writing newParent = writingRepository.findById(request.getParentBookId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Parent book not found with id: " + request.getParentBookId()));
            writing.setParentBook(newParent);
            writing.setSeriesId(newParent.getSeriesId());
        }

        // ✅ Update languages and content
        if (request.getContentLanguages() != null) {
            writing.setContentLanguages(new LinkedHashSet<>(request.getContentLanguages()));
        }
        applyContentByLanguages(writing, request, ckbCoverUrl, kmrCoverUrl, ckbFileUrl, kmrFileUrl);

        // ✅ Update bilingual tags/keywords
        replaceBilingualSets(writing, request);

        Writing updated = writingRepository.save(writing);

        // ✅ Update series counts if series changed
        if (oldSeriesId != null && !oldSeriesId.equals(updated.getSeriesId())) {
            updateSeriesCount(oldSeriesId);
            updateSeriesCount(updated.getSeriesId());
        } else if (updated.getSeriesId() != null) {
            updateSeriesCount(updated.getSeriesId());
        }

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
        String seriesId = writing.getSeriesId();

        logAction(writing, "DELETED", String.format("Writing '%s' deleted", title));
        writingRepository.delete(writing);

        // ✅ Update series count after deletion
        if (seriesId != null) {
            updateSeriesCount(seriesId);
        }
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
        log.info("Searching by tag: '{}' | language: {}", cleanTag, language);

        Page<Writing> results;

        if ("ckb".equalsIgnoreCase(language)) {
            results = writingRepository.findByTagCkb(cleanTag, pageable);
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = writingRepository.findByTagKmr(cleanTag, pageable);
        } else {
            results = writingRepository.findByTagInBothLanguages(cleanTag, pageable);
        }

        return results.map(this::mapToResponse);
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
        log.info("Searching by keyword: '{}' | language: {}", cleanKeyword, language);

        Page<Writing> results;

        if ("ckb".equalsIgnoreCase(language)) {
            results = writingRepository.findByKeywordCkb(cleanKeyword, pageable);
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = writingRepository.findByKeywordKmr(cleanKeyword, pageable);
        } else {
            results = writingRepository.findByKeywordInBothLanguages(cleanKeyword, pageable);
        }

        return results.map(this::mapToResponse);
    }

    // ============================================================
    // ✅ HELPER: UPDATE SERIES COUNT
    // ============================================================

    @Transactional
    protected void updateSeriesCount(String seriesId) {
        if (seriesId == null) return;

        Long count = writingRepository.countBySeriesId(seriesId);

        // Update all books in the series with the new count
        List<Writing> seriesBooks = writingRepository.findBySeriesIdOrderBySeriesOrderAsc(seriesId);
        seriesBooks.forEach(book -> book.setSeriesTotalBooks(count.intValue()));
        writingRepository.saveAll(seriesBooks);

        log.debug("Updated series count for '{}': {} books", seriesId, count);
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private void validate(CreateRequest request, boolean isCreate) {
        if (request.getContentLanguages() == null || request.getContentLanguages().isEmpty()) {
            throw new IllegalArgumentException("At least one content language is required");
        }

        for (Language lang : request.getContentLanguages()) {
            LanguageContentDto content = (lang == Language.CKB) ? request.getCkbContent() : request.getKmrContent();
            if (content == null) {
                throw new IllegalArgumentException("Content for language " + lang + " is required but missing");
            }
            if (isCreate && isBlank(content.getTitle())) {
                throw new IllegalArgumentException("Title is required for language " + lang);
            }
        }
    }

    private void validate(UpdateRequest request) {
        // Update validation is more lenient - only validate if languages are provided
        if (request.getContentLanguages() != null) {
            for (Language lang : request.getContentLanguages()) {
                LanguageContentDto content = (lang == Language.CKB) ? request.getCkbContent() : request.getKmrContent();
                if (content == null) {
                    throw new IllegalArgumentException("Content for language " + lang + " is required but missing");
                }
            }
        }
    }

    // ============================================================
    // CONTENT APPLICATION
    // ============================================================

    private void applyContentByLanguages(Writing writing, CreateRequest request,
                                         String ckbCoverUrl, String kmrCoverUrl,
                                         String ckbFileUrl, String kmrFileUrl) {
        if (request.getContentLanguages().contains(Language.CKB)) {
            writing.setCkbContent(buildContent(request.getCkbContent(), ckbCoverUrl, ckbFileUrl));
        }
        if (request.getContentLanguages().contains(Language.KMR)) {
            writing.setKmrContent(buildContent(request.getKmrContent(), kmrCoverUrl, kmrFileUrl));
        }
    }

    private void applyContentByLanguages(Writing writing, UpdateRequest request,
                                         String ckbCoverUrl, String kmrCoverUrl,
                                         String ckbFileUrl, String kmrFileUrl) {
        if (request.getCkbContent() != null) {
            writing.setCkbContent(updateContent(writing.getCkbContent(), request.getCkbContent(), ckbCoverUrl, ckbFileUrl));
        }
        if (request.getKmrContent() != null) {
            writing.setKmrContent(updateContent(writing.getKmrContent(), request.getKmrContent(), kmrCoverUrl, kmrFileUrl));
        }
    }

    private WritingContent buildContent(LanguageContentDto dto, String coverUrl, String fileUrl) {
        if (dto == null) return null;
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

        // ✅ Series Info
        if (writing.getSeriesId() != null) {
            response.setSeriesInfo(SeriesInfoDto.builder()
                    .seriesId(writing.getSeriesId())
                    .seriesName(writing.getSeriesName())
                    .seriesOrder(writing.getSeriesOrder())
                    .parentBookId(writing.getParentBook() != null ? writing.getParentBook().getId() : null)
                    .totalBooks(writing.getSeriesTotalBooks())
                    .isParent(writing.isSeriesParent())
                    .build());
        }

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