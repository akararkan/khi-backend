package ak.dev.khi_backend.khi_app.service.publishment.writing;

import ak.dev.khi_backend.khi_app.dto.publishment.writing.WritingDtos.*;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.exceptions.NotFoundException;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import ak.dev.khi_backend.khi_app.model.publishment.writing.Writing;
import ak.dev.khi_backend.khi_app.model.publishment.writing.WritingContent;
import ak.dev.khi_backend.khi_app.model.publishment.writing.WritingLog;
import ak.dev.khi_backend.khi_app.repository.publishment.topic.PublishmentTopicRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.writing.WritingLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.writing.WritingRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * سێرڤیسی نووسراو - بەڕێوەبردنی کتێب و نووسراوەکان لەگەڵ زنجیرە (Series) و بابەتەکان
 *
 * NOTE: WritingTopic enum + field renamed to BookGenre / bookGenre throughout.
 *       Update WritingDtos (CreateRequest, UpdateRequest, Response) accordingly:
 *         • WritingTopic writingTopic  →  BookGenre bookGenre
 *         • getWritingTopic()          →  getBookGenre()
 *
 * ─── Performance fix ──────────────────────────────────────────────────────────
 *  All single-entity lookups now go through findOrThrow(id) which calls
 *  writingRepository.findByIdWithDetails(id) — a single query that JOIN FETCHes
 *  seriesBooks, parentBook, and topic instead of firing N+1 lazy selects.
 *  The EAGER element collections (tags, keywords, languages) are batch-loaded
 *  by Hibernate via @BatchSize(size=25) on Writing.java.
 *
 * لیستی هەڵە کوردیەکان کە بەکاردێن:
 *
 * ١. "writing.languages.required"     — زمانەکانی ناوەڕۆک پێویستە
 * ٢. "writing.content.missing"        — ناوەڕۆک کەمە
 * ٣. "writing.title.required"         — ناونیشانی نووسراو پێویستە
 * ٤. "writing.not_found"              — نووسراو نەدۆزرایەوە
 * ٥. "topic.not_found"                — بابەت نەدۆزرایەوە
 * ٦. "parent_book.not_found"          — کتێبی باوک نەدۆزرایەوە
 * ٧. "series.not_found"               — زنجیرە نەدۆزرایەوە
 * ٨. "media.upload.failed"            — شکستی ناردنی فایل
 * ٩. "search.writer.required"         — ناوی نووسەر پێویستە
 * ١٠. "search.tag.required"           — تاگی گەڕان پێویستە
 * ١١. "search.keyword.required"       — کلیلەووشەی گەڕان پێویستە
 * ١٢. "writing.topic.names.required"  — ناوی بابەت پێویستە
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WritingService {

    private static final String ENTITY_TYPE = "WRITING";

    private final WritingRepository          writingRepository;
    private final WritingLogRepository       writingLogRepository;
    private final PublishmentTopicRepository topicRepository;
    private final S3Service                  s3Service;
    private final ObjectMapper               objectMapper;

    // =========================================================================
    // دروستکردن
    // =========================================================================

    /**
     * دروستکردنی نووسراوێکی نوێ (کتێب یان نووسین)
     *
     * @param request         زانیاری JSON
     * @param ckbCoverImage   وێنەی بەرگی سۆرانی (ئارەزوومەندانە)
     * @param kmrCoverImage   وێنەی بەرگی کورمانجی (ئارەزوومەندانە)
     * @param hoverCoverImage وێنەی هاڤەر (ئارەزوومەندانە)
     * @param ckbBookFile     فایلی کتێبی سۆرانی (PDF یان جۆرەکانی تر)
     * @param kmrBookFile     فایلی کتێبی کورمانجی
     *
     * @throws BadRequestException - "زمانەکانی ناوەڕۆک پێویستە"
     * @throws BadRequestException - "ناوەڕۆک کەمە"
     * @throws BadRequestException - "ناونیشانی نووسراو پێویستە"
     * @throws BadRequestException - "شکستی ناردنی فایل"
     * @throws NotFoundException   - "کتێبی باوک نەدۆزرایەوە"
     */
    @Transactional
    public Response addWriting(CreateRequest request,
                               MultipartFile ckbCoverImage,
                               MultipartFile kmrCoverImage,
                               MultipartFile hoverCoverImage,
                               MultipartFile ckbBookFile,
                               MultipartFile kmrBookFile) {

        log.info("دروستکردنی نووسراو: {}", getCombinedTitle(request));

        validate(request, true);

        // ─── ناردنی وێنەی بەرگ ────────────────────────────────────────────────
        String ckbCoverUrl   = uploadOrFallback(ckbCoverImage,   request.getCkbCoverUrl(),   "وێنەی بەرگی CKB");
        String kmrCoverUrl   = uploadOrFallback(kmrCoverImage,   request.getKmrCoverUrl(),   "وێنەی بەرگی KMR");
        String hoverCoverUrl = uploadOrFallback(hoverCoverImage, request.getHoverCoverUrl(), "وێنەی هاڤەر");

        // ─── ناردنی فایلی کتێب ────────────────────────────────────────────────
        String ckbFileUrl = uploadFile(ckbBookFile, "فایلی کتێبی CKB");
        String kmrFileUrl = uploadFile(kmrBookFile, "فایلی کتێبی KMR");

        // ─── دۆزینەوەی بابەت ─────────────────────────────────────────────────
        PublishmentTopic topic = resolveTopic(request.getTopicId(), request.getNewTopic());

        // ─── دۆزینەوەی کتێبی باوک (بۆ زنجیرە) ───────────────────────────────
        // ✅ uses findOrThrow → findByIdWithDetails (single query, no N+1)
        Writing parentBook  = null;
        String  seriesId    = request.getSeriesId();
        Double  seriesOrder = request.getSeriesOrder();

        if (request.getParentBookId() != null) {
            parentBook  = findOrThrow(request.getParentBookId(), "parent_book.not_found");
            seriesId    = parentBook.getSeriesId();
            if (seriesOrder == null) {
                Double maxOrder = writingRepository.findMaxSeriesOrder(seriesId);
                seriesOrder = (maxOrder != null ? maxOrder : 0.0) + 1.0;
            }
        }

        // ─── دروستکردنی ئینتیتی ──────────────────────────────────────────────
        Writing writing = Writing.builder()
                .ckbCoverUrl(ckbCoverUrl)
                .kmrCoverUrl(kmrCoverUrl)
                .hoverCoverUrl(hoverCoverUrl)
                .topic(topic)
                .bookGenre(request.getBookGenre())
                .contentLanguages(new LinkedHashSet<>(safeLangs(request.getContentLanguages())))
                .publishedByInstitute(request.isPublishedByInstitute())
                .tagsCkb(new LinkedHashSet<>(safeSet(request.getTags()     != null ? request.getTags().getCkb()     : null)))
                .tagsKmr(new LinkedHashSet<>(safeSet(request.getTags()     != null ? request.getTags().getKmr()     : null)))
                .keywordsCkb(new LinkedHashSet<>(safeSet(request.getKeywords() != null ? request.getKeywords().getCkb() : null)))
                .keywordsKmr(new LinkedHashSet<>(safeSet(request.getKeywords() != null ? request.getKeywords().getKmr() : null)))
                .seriesId(seriesId)
                .seriesName(request.getSeriesName())
                .seriesOrder(seriesOrder)
                .parentBook(parentBook)
                .build();

        applyContent(writing, request, ckbFileUrl, kmrFileUrl);

        Writing saved = writingRepository.save(writing);
        updateSeriesCount(saved.getSeriesId());

        logAction(saved, "CREATED", "نووسراو '" + getCombinedTitle(saved) + "' دروستکرا");
        log.info("نووسراو دروستکرا — id={}, زنجیرە={}", saved.getId(), saved.getSeriesId());
        return mapToResponse(saved);
    }

    // =========================================================================
    // نوێکردنەوە
    // =========================================================================

    /**
     * نوێکردنەوەی نووسراوێکی بەرەبەڕە
     *
     * @throws NotFoundException   - "نووسراو نەدۆزرایەوە"
     * @throws BadRequestException - "ناوەڕۆک کەمە"
     * @throws NotFoundException   - "کتێبی باوک نەدۆزرایەوە"
     * @throws BadRequestException - "شکستی ناردنی فایل"
     */
    @Transactional
    public Response updateWriting(Long id,
                                  UpdateRequest request,
                                  MultipartFile ckbCoverImage,
                                  MultipartFile kmrCoverImage,
                                  MultipartFile hoverCoverImage,
                                  MultipartFile ckbBookFile,
                                  MultipartFile kmrBookFile) {

        log.info("نوێکردنەوەی نووسراو id={}", id);

        // ✅ single query: JOIN FETCHes seriesBooks + parentBook + topic
        Writing writing = findOrThrow(id, "writing.not_found");

        validate(request);

        // ─── وێنەی بەرگ ──────────────────────────────────────────────────────
        writing.setCkbCoverUrl(resolveUpdate(ckbCoverImage,   request.getCkbCoverUrl(),   writing.getCkbCoverUrl()));
        writing.setKmrCoverUrl(resolveUpdate(kmrCoverImage,   request.getKmrCoverUrl(),   writing.getKmrCoverUrl()));
        writing.setHoverCoverUrl(resolveUpdate(hoverCoverImage, request.getHoverCoverUrl(), writing.getHoverCoverUrl()));

        // ─── فایلی کتێب ──────────────────────────────────────────────────────
        String ckbFileUrl = uploadFile(ckbBookFile, "فایلی کتێبی CKB");
        String kmrFileUrl = uploadFile(kmrBookFile, "فایلی کتێبی KMR");

        // ─── بابەت ───────────────────────────────────────────────────────────
        if (Boolean.TRUE.equals(request.getClearTopic())) {
            writing.setTopic(null);
        } else {
            PublishmentTopic topic = resolveTopic(request.getTopicId(), request.getNewTopic());
            if (topic != null) writing.setTopic(topic);
        }

        // ─── خانە هاوبەشەکان ─────────────────────────────────────────────────
        if (request.getBookGenre()            != null) writing.setBookGenre(request.getBookGenre());
        if (request.getPublishedByInstitute() != null) writing.setPublishedByInstitute(request.getPublishedByInstitute());

        // ─── زمان و ناوەڕۆک ──────────────────────────────────────────────────
        if (request.getContentLanguages() != null && !request.getContentLanguages().isEmpty()) {
            writing.setContentLanguages(new LinkedHashSet<>(request.getContentLanguages()));
        }
        applyContent(writing, request, ckbFileUrl, kmrFileUrl);

        // ─── تاگ و کلیلەووشەکان ──────────────────────────────────────────────
        replaceBilingualSets(writing, request);

        // ─── زنجیرە ──────────────────────────────────────────────────────────
        String oldSeriesId = writing.getSeriesId();
        if (request.getSeriesName()  != null) writing.setSeriesName(request.getSeriesName());
        if (request.getSeriesOrder() != null) writing.setSeriesOrder(request.getSeriesOrder());
        if (request.getParentBookId() != null) {
            // ✅ single query for parent book too
            Writing newParent = findOrThrow(request.getParentBookId(), "parent_book.not_found");
            writing.setParentBook(newParent);
            writing.setSeriesId(newParent.getSeriesId());
        }

        Writing updated = writingRepository.save(writing);

        if (oldSeriesId != null && !oldSeriesId.equals(updated.getSeriesId())) {
            updateSeriesCount(oldSeriesId);
        }
        updateSeriesCount(updated.getSeriesId());

        logAction(updated, "UPDATED", "نووسراو '" + getCombinedTitle(updated) + "' نوێکرایەوە");
        return mapToResponse(updated);
    }

    // =========================================================================
    // سڕینەوە
    // =========================================================================

    /**
     * سڕینەوەی نووسراو بە تەواوی
     *
     * @throws NotFoundException - "نووسراو نەدۆزرایەوە"
     */
    @Transactional
    public void deleteWriting(Long id) {
        log.info("سڕینەوەی نووسراو id={}", id);

        // ✅ findOrThrow handles null check + not-found in one place
        Writing writing = findOrThrow(id, "writing.not_found");

        String seriesId  = writing.getSeriesId();
        String title     = getCombinedTitle(writing);
        Long   writingId = writing.getId();

        // ── سەرەتا: تۆمارکردنی لۆگ ───────────────────────────────────────────
        WritingLog deletionLog = WritingLog.builder()
                .writing(null)
                .writingId(writingId)
                .action("DELETED")
                .actorId("system")
                .actorName("System")
                .details("نووسراو '" + title + "' سڕایەوە")
                .createdAt(LocalDateTime.now())
                .build();
        writingLogRepository.saveAndFlush(deletionLog);

        // ── دواتر: سڕینەوە ───────────────────────────────────────────────────
        writingRepository.delete(writing);
        writingRepository.flush();

        // ── کۆتایی: نوێکردنەوەی ژمارەی زنجیرە ───────────────────────────────
        updateSeriesCount(seriesId);
    }

    // =========================================================================
    // خوێندنەوە — هەموو / بەپێی ئایدی
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<Response> getAllWritings(Pageable pageable) {
        // ✅ uses findAllWithTopic → avoids N+1 on topic name in list cards
        return writingRepository.findAllWithTopic(pageable).map(this::mapToResponse);
    }

    /**
     * هێنانی نووسراو بەپێی ئایدی — هەموو وردەکاریەکان بەیەک دەمەوە
     *
     * @throws NotFoundException - "نووسراو نەدۆزرایەوە"
     */
    @Transactional(readOnly = true)
    public Response getWritingById(Long id) {
        // ✅ single query: JOIN FETCHes seriesBooks + parentBook + topic
        //    EAGER sets batch-loaded via @BatchSize(size=25) on Writing.java
        return mapToResponse(findOrThrow(id, "writing.not_found"));
    }

    // =========================================================================
    // کردارەکانی زنجیرە (Series)
    // =========================================================================

    /**
     * @throws NotFoundException - "نووسراو نەدۆزرایەوە" | "parent_book.not_found"
     */
    @Transactional
    public Response linkBookToSeries(LinkToSeriesRequest request) {
        // ✅ both lookups use findOrThrow
        Writing book   = findOrThrow(request.getBookId(),       "writing.not_found");
        Writing parent = findOrThrow(request.getParentBookId(), "parent_book.not_found");

        book.setParentBook(parent);
        book.setSeriesId(parent.getSeriesId());
        book.setSeriesOrder(request.getSeriesOrder());
        book.setSeriesName(request.getSeriesName() != null ? request.getSeriesName() : parent.getSeriesName());

        Writing updated = writingRepository.save(book);
        updateSeriesCount(updated.getSeriesId());
        logAction(updated, "LINKED_TO_SERIES", "لکێندراوە بە زنجیرە " + parent.getSeriesId());
        return mapToResponse(updated);
    }

    /**
     * @throws NotFoundException - "series.not_found"
     */
    @Transactional(readOnly = true)
    public SeriesResponse getSeriesBooks(String seriesId) {
        List<Writing> books = writingRepository.findBySeriesIdOrderBySeriesOrderAsc(seriesId);
        if (books.isEmpty()) {
            throw new NotFoundException("series.not_found", Map.of("seriesId", seriesId));
        }

        String seriesName = books.get(0).getEffectiveSeriesName();
        List<SeriesBookSummary> summaries = books.stream()
                .map(b -> SeriesBookSummary.builder()
                        .id(b.getId())
                        .titleCkb(b.getCkbContent() != null ? b.getCkbContent().getTitle() : null)
                        .titleKmr(b.getKmrContent() != null ? b.getKmrContent().getTitle() : null)
                        .seriesOrder(b.getSeriesOrder())
                        .createdAt(b.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return SeriesResponse.builder()
                .seriesId(seriesId)
                .seriesName(seriesName)
                .totalBooks(books.size())
                .books(summaries)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<Response> getAllSeriesParents(Pageable pageable) {
        return writingRepository.findSeriesParents(pageable).map(this::mapToResponse);
    }

    // =========================================================================
    // گەڕان
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<Response> searchByWriter(String writerName, String language, Pageable pageable) {
        if (isBlank(writerName)) throw new BadRequestException("search.writer.required", Map.of("field", "writerName"));
        String q = writerName.trim();
        Page<Writing> results;
        if ("ckb".equalsIgnoreCase(language))      results = writingRepository.findByWriterCkbContainingIgnoreCase(q, pageable);
        else if ("kmr".equalsIgnoreCase(language)) results = writingRepository.findByWriterKmrContainingIgnoreCase(q, pageable);
        else                                       results = writingRepository.findByWriterInBothLanguages(q, pageable);
        return results.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<Response> searchByTag(String tag, String language, Pageable pageable) {
        if (isBlank(tag)) throw new BadRequestException("search.tag.required", Map.of("field", "tag"));
        String q = tag.trim();
        Page<Writing> results;
        if ("ckb".equalsIgnoreCase(language))      results = writingRepository.findByTagCkb(q, pageable);
        else if ("kmr".equalsIgnoreCase(language)) results = writingRepository.findByTagKmr(q, pageable);
        else                                       results = writingRepository.findByTagInBothLanguages(q, pageable);
        return results.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<Response> searchByKeyword(String keyword, String language, Pageable pageable) {
        if (isBlank(keyword)) throw new BadRequestException("search.keyword.required", Map.of("field", "keyword"));
        String q = keyword.trim();
        Page<Writing> results;
        if ("ckb".equalsIgnoreCase(language))      results = writingRepository.findByKeywordCkb(q, pageable);
        else if ("kmr".equalsIgnoreCase(language)) results = writingRepository.findByKeywordKmr(q, pageable);
        else                                       results = writingRepository.findByKeywordInBothLanguages(q, pageable);
        return results.map(this::mapToResponse);
    }

    // =========================================================================
    // دۆزینەوەی بابەت
    // =========================================================================

    private PublishmentTopic resolveTopic(Long topicId, TopicPayload newTopic) {
        if (topicId != null) {
            return topicRepository.findById(topicId)
                    .orElseThrow(() -> new NotFoundException("topic.not_found", Map.of("topicId", topicId)));
        }
        if (newTopic != null && (!isBlank(newTopic.getNameCkb()) || !isBlank(newTopic.getNameKmr()))) {
            PublishmentTopic created = PublishmentTopic.builder()
                    .entityType(ENTITY_TYPE)
                    .nameCkb(trimOrNull(newTopic.getNameCkb()))
                    .nameKmr(trimOrNull(newTopic.getNameKmr()))
                    .build();
            created = topicRepository.save(created);
            log.info("بابەتی inline دروستکرا — id={}, ckb='{}', kmr='{}'",
                    created.getId(), created.getNameCkb(), created.getNameKmr());
            return created;
        }
        return null;
    }

    // =========================================================================
    // ژماری زنجیرە
    // =========================================================================

    @Transactional
    protected void updateSeriesCount(String seriesId) {
        if (isBlank(seriesId)) return;
        Long count = writingRepository.countBySeriesId(seriesId);
        List<Writing> books = writingRepository.findBySeriesIdOrderBySeriesOrderAsc(seriesId);
        books.forEach(b -> b.setSeriesTotalBooks(count.intValue()));
        writingRepository.saveAll(books);
    }

    // =========================================================================
    // پشتڕاستکردنەوە
    // =========================================================================

    private void validate(CreateRequest request, boolean isCreate) {
        if (request.getContentLanguages() == null || request.getContentLanguages().isEmpty()) {
            throw new BadRequestException("writing.languages.required", Map.of("field", "contentLanguages"));
        }
        for (Language lang : request.getContentLanguages()) {
            LanguageContentDto content = lang == Language.CKB ? request.getCkbContent() : request.getKmrContent();
            if (content == null) {
                throw new BadRequestException("writing.content.missing",
                        Map.of("language", lang, "message", "ناوەڕۆک بۆ " + lang + " دیاری نەکراوە"));
            }
            if (isCreate && isBlank(content.getTitle())) {
                throw new BadRequestException("writing.title.required",
                        Map.of("language", lang, "message", "ناونیشان بۆ " + lang + " پێویستە"));
            }
        }
    }

    private void validate(UpdateRequest request) {
        if (request.getContentLanguages() == null) return;
        for (Language lang : request.getContentLanguages()) {
            LanguageContentDto content = lang == Language.CKB ? request.getCkbContent() : request.getKmrContent();
            if (content == null) throw new BadRequestException("writing.content.missing", Map.of("language", lang));
        }
    }

    // =========================================================================
    // ناوەڕۆک
    // =========================================================================

    private void applyContent(Writing writing, CreateRequest request, String ckbFileUrl, String kmrFileUrl) {
        if (request.getContentLanguages().contains(Language.CKB)) {
            writing.setCkbContent(buildContent(request.getCkbContent(), ckbFileUrl));
        }
        if (request.getContentLanguages().contains(Language.KMR)) {
            writing.setKmrContent(buildContent(request.getKmrContent(), kmrFileUrl));
        }
    }

    private void applyContent(Writing writing, UpdateRequest request, String ckbFileUrl, String kmrFileUrl) {
        if (request.getCkbContent() != null) {
            writing.setCkbContent(mergeContent(writing.getCkbContent(), request.getCkbContent(), ckbFileUrl));
        }
        if (request.getKmrContent() != null) {
            writing.setKmrContent(mergeContent(writing.getKmrContent(), request.getKmrContent(), kmrFileUrl));
        }
    }

    private WritingContent buildContent(LanguageContentDto dto, String fileUrl) {
        if (dto == null) return null;
        return WritingContent.builder()
                .title(trimOrNull(dto.getTitle()))
                .description(trimOrNull(dto.getDescription()))
                .writer(trimOrNull(dto.getWriter()))
                .fileUrl(fileUrl != null ? fileUrl : trimOrNull(dto.getFileUrl()))
                .fileFormat(dto.getFileFormat())
                .fileSizeBytes(dto.getFileSizeBytes())
                .pageCount(dto.getPageCount())
                .genre(trimOrNull(dto.getGenre()))
                .build();
    }

    private WritingContent mergeContent(WritingContent existing, LanguageContentDto dto, String fileUrl) {
        if (existing == null) return buildContent(dto, fileUrl);
        if (dto.getTitle()         != null) existing.setTitle(trimOrNull(dto.getTitle()));
        if (dto.getDescription()   != null) existing.setDescription(trimOrNull(dto.getDescription()));
        if (dto.getWriter()        != null) existing.setWriter(trimOrNull(dto.getWriter()));
        if (fileUrl                != null) existing.setFileUrl(fileUrl);
        else if (dto.getFileUrl()  != null) existing.setFileUrl(trimOrNull(dto.getFileUrl()));
        if (dto.getFileFormat()    != null) existing.setFileFormat(dto.getFileFormat());
        if (dto.getFileSizeBytes() != null) existing.setFileSizeBytes(dto.getFileSizeBytes());
        if (dto.getPageCount()     != null) existing.setPageCount(dto.getPageCount());
        if (dto.getGenre()         != null) existing.setGenre(trimOrNull(dto.getGenre()));
        return existing;
    }

    private void replaceBilingualSets(Writing writing, UpdateRequest request) {
        if (request.getTags() != null) {
            if (request.getTags().getCkb() != null) { writing.getTagsCkb().clear(); writing.getTagsCkb().addAll(cleanStrings(request.getTags().getCkb())); }
            if (request.getTags().getKmr() != null) { writing.getTagsKmr().clear(); writing.getTagsKmr().addAll(cleanStrings(request.getTags().getKmr())); }
        }
        if (request.getKeywords() != null) {
            if (request.getKeywords().getCkb() != null) { writing.getKeywordsCkb().clear(); writing.getKeywordsCkb().addAll(cleanStrings(request.getKeywords().getCkb())); }
            if (request.getKeywords().getKmr() != null) { writing.getKeywordsKmr().clear(); writing.getKeywordsKmr().addAll(cleanStrings(request.getKeywords().getKmr())); }
        }
    }

    // =========================================================================
    // گۆڕین بۆ Response
    // =========================================================================

    private Response mapToResponse(Writing w) {
        Response r = Response.builder()
                .id(w.getId())
                .contentLanguages(w.getContentLanguages() != null
                        ? new LinkedHashSet<>(w.getContentLanguages()) : new LinkedHashSet<>())
                .ckbCoverUrl(w.getCkbCoverUrl())
                .kmrCoverUrl(w.getKmrCoverUrl())
                .hoverCoverUrl(w.getHoverCoverUrl())
                .topic(w.getTopic() != null
                        ? TopicInfo.builder()
                        .id(w.getTopic().getId())
                        .nameCkb(w.getTopic().getNameCkb())
                        .nameKmr(w.getTopic().getNameKmr())
                        .build()
                        : null)
                .bookGenre(w.getBookGenre())
                .publishedByInstitute(w.isPublishedByInstitute())
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();

        if (w.getCkbContent() != null) {
            WritingContent c = w.getCkbContent();
            r.setCkbContent(LanguageContentDto.builder()
                    .title(c.getTitle()).description(c.getDescription()).writer(c.getWriter())
                    .fileUrl(c.getFileUrl()).fileFormat(c.getFileFormat())
                    .fileSizeBytes(c.getFileSizeBytes()).pageCount(c.getPageCount()).genre(c.getGenre())
                    .build());
        }

        if (w.getKmrContent() != null) {
            WritingContent c = w.getKmrContent();
            r.setKmrContent(LanguageContentDto.builder()
                    .title(c.getTitle()).description(c.getDescription()).writer(c.getWriter())
                    .fileUrl(c.getFileUrl()).fileFormat(c.getFileFormat())
                    .fileSizeBytes(c.getFileSizeBytes()).pageCount(c.getPageCount()).genre(c.getGenre())
                    .build());
        }

        r.setTags(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(w.getTagsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(w.getTagsKmr())))
                .build());

        r.setKeywords(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(w.getKeywordsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(w.getKeywordsKmr())))
                .build());

        if (w.getSeriesId() != null) {
            r.setSeriesInfo(SeriesInfoDto.builder()
                    .seriesId(w.getSeriesId())
                    .seriesName(w.getSeriesName())
                    .seriesOrder(w.getSeriesOrder())
                    .parentBookId(w.getParentBook() != null ? w.getParentBook().getId() : null)
                    .totalBooks(w.getSeriesTotalBooks())
                    .isParent(w.isSeriesParent())
                    .build());
        }

        return r;
    }

    // =========================================================================
    // یاریدەدەرەکانی ناردنی فایل
    // =========================================================================

    private String uploadFile(MultipartFile file, String description) {
        if (file == null || file.isEmpty()) return null;
        try {
            String url = s3Service.upload(file.getBytes(), file.getOriginalFilename(), file.getContentType());
            log.info("{} نێردرا → {}", description, url);
            return url;
        } catch (IOException e) {
            throw new BadRequestException("media.upload.failed",
                    Map.of("description", description, "error", e.getMessage()));
        }
    }

    private String uploadOrFallback(MultipartFile file, String urlFallback, String description) {
        String uploaded = uploadFile(file, description);
        return uploaded != null ? uploaded : trimOrNull(urlFallback);
    }

    private String resolveUpdate(MultipartFile file, String requestUrl, String existing) {
        String uploaded = uploadFile(file, "وێنەی بەرگ");
        if (uploaded != null) return uploaded;
        if (requestUrl != null) { String t = requestUrl.trim(); return t.isEmpty() ? null : t; }
        return existing;
    }

    // =========================================================================
    // تۆمارکردنی چالاکی
    // =========================================================================

    private void logAction(Writing writing, String action, String details) {
        writingLogRepository.save(WritingLog.builder()
                .writing(writing)
                .writingId(writing.getId())
                .action(action)
                .actorId("system")
                .actorName("System")
                .details(details)
                .createdAt(LocalDateTime.now())
                .build());
    }

    // =========================================================================
    // یاریدەدەرەکان
    // =========================================================================

    /**
     * دۆزینەوەی نووسراو بە تەواوی وردەکارییەکانی — یەک کوێری بەشیوەی JOIN FETCH.
     *
     * ✅ JOIN FETCHes: seriesBooks + parentBook + topic  (هەموو LAZY-ەکان)
     * ✅ EAGER element collections (tags, keywords, languages) batch-loaded
     *    via @BatchSize(size=25) — 5 SELECTs بۆ هەموو پەیجێک بجیاتی لە N×5
     *
     * @throws NotFoundException ئەگەر ئایدی نەدۆزرایەوە، بەپێی errorCode
     */
    private Writing findOrThrow(Long id, String errorCode) {
        return writingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException(errorCode, Map.of("id", id)));
    }

    private String getCombinedTitle(Writing w) {
        if (w.getCkbContent() != null && !isBlank(w.getCkbContent().getTitle())) return w.getCkbContent().getTitle();
        if (w.getKmrContent() != null && !isBlank(w.getKmrContent().getTitle())) return w.getKmrContent().getTitle();
        return "ناونیشانی نەناسراو";
    }

    private String getCombinedTitle(CreateRequest r) {
        if (r.getCkbContent() != null && !isBlank(r.getCkbContent().getTitle())) return r.getCkbContent().getTitle();
        if (r.getKmrContent() != null && !isBlank(r.getKmrContent().getTitle())) return r.getKmrContent().getTitle();
        return "ناونیشانی نەناسراو";
    }

    private boolean isBlank(String s)    { return s == null || s.isBlank(); }
    private String trimOrNull(String s)   { if (s == null) return null; String t = s.trim(); return t.isEmpty() ? null : t; }
    private Set<Language> safeLangs(Set<Language> l) { return l == null ? Set.of() : l; }
    private <T> Set<T> safeSet(Set<T> s)             { return s == null ? Set.of() : s; }
    private Set<String> cleanStrings(Set<String> in) {
        if (in == null || in.isEmpty()) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : in) { if (s != null && !s.isBlank()) out.add(s.trim()); }
        return out;
    }
}