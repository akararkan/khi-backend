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
 * لیستی هەڵە کوردیەکان کە بەکاردێن:
 *
 * ١. "writing.languages.required" = "زمانەکانی ناوەڕۆک پێویستە: دەبێت لانیکەم یەک زمان هەڵبژێردرێت" (Bad Request)
 * ٢. "writing.content.missing" = "ناوەڕۆک کەمە: ناوەڕۆک بۆ ئەم زمانە دیاری نەکراوە" (Bad Request)
 * ٣. "writing.title.required" = "ناونیشانی نووسراو پێویستە: کاتێک زمان چالاکە دەبێت ناونیشان هەبێت" (Bad Request)
 * ٤. "writing.not_found" = "نووسراو نەدۆزرایەوە: ئایدییەکە بوونی نییە لە سیستەم" (Not Found)
 * ٥. "topic.not_found" = "بابەت نەدۆزرایەوە: ئایدیی بابەت بوونی نییە" (Not Found)
 * ٦. "parent_book.not_found" = "کتێبی باوک نەدۆزرایەوە: ئایدیی کتێبی باوک بوونی نییە" (Not Found)
 * ٧. "series.not_found" = "زنجیرە نەدۆزرایەوە: ناسنامەی زنجیرە بوونی نییە" (Not Found)
 * ٨. "media.upload.failed" = "شکستی ناردنی فایل: کێشە لە ناردنی فایل بۆ S3" (Bad Request)
 * ٩. "search.writer.required" = "ناوی نووسەر پێویستە بۆ گەڕان" (Bad Request)
 * ١٠. "search.tag.required" = "تاگی گەڕان پێویستە" (Bad Request)
 * ١١. "search.keyword.required" = "کلیلەووشەی گەڕان پێویستە" (Bad Request)
 * ١٢. "writing.topic.names.required" = "ناوی بابەت پێویستە: دەبێت لانیکەم ناوێکی کوردی بنووسرێت" (Bad Request)
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
     * @param request        زانیاری JSON
     * @param ckbCoverImage  وێنەی بەرگی کوردیی ناوەندی (ئارەزوومەندانە، لەسەر لینک پێشەکییە)
     * @param kmrCoverImage  وێنەی بەرگی کوردیی باکووری (ئارەزوومەندانە)
     * @param hoverCoverImage وێنەی هاڤەر (ئارەزوومەندانە)
     * @param ckbBookFile    فایلی کتێبی کوردیی ناوەندی (PDF یان جۆرەکانی تر)
     * @param kmrBookFile    فایلی کتێبی کوردیی باکووری
     *
     * @throws BadRequestException - "زمانەکانی ناوەڕۆک پێویستە"
     * @throws BadRequestException - "ناوەڕۆک کەمە"
     * @throws BadRequestException - "ناونیشانی نووسراو پێویستە"
     * @throws BadRequestException - "شکستی ناردنی فایل"
     * @throws NotFoundException   - "کتێبی باوک نەدۆزرایەوە" (ئەگەر seriesId دیاری کرابێت)
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

        // ─── ناردنی وێنەی بەرگ (فایل لەسەر لینک پێشەکییە) ────────────────────
        String ckbCoverUrl   = uploadOrFallback(ckbCoverImage,   request.getCkbCoverUrl(),   "وێنەی بەرگی CKB");
        String kmrCoverUrl   = uploadOrFallback(kmrCoverImage,   request.getKmrCoverUrl(),   "وێنەی بەرگی KMR");
        String hoverCoverUrl = uploadOrFallback(hoverCoverImage, request.getHoverCoverUrl(), "وێنەی هاڤەر");

        // ─── ناردنی فایلی کتێب ────────────────────────────────────────────────
        String ckbFileUrl = uploadFile(ckbBookFile, "فایلی کتێبی CKB");
        String kmrFileUrl = uploadFile(kmrBookFile, "فایلی کتێبی KMR");

        // ─── دۆزینەوەی بابەت ────────────────────────────────────────────────────
        PublishmentTopic topic = resolveTopic(request.getTopicId(), request.getNewTopic());

        // ─── دۆزینەوەی کتێبی باوک (بۆ زنجیرە) ───────────────────────────────────
        Writing parentBook = null;
        String  seriesId   = request.getSeriesId();
        Double  seriesOrder = request.getSeriesOrder();

        if (request.getParentBookId() != null) {
            parentBook = writingRepository.findById(request.getParentBookId())
                    .orElseThrow(() -> {
                        // هەڵە: کتێبی باوک نەدۆزرایەوە
                        return new NotFoundException("parent_book.not_found",
                                Map.of("parentBookId", request.getParentBookId()));
                    });
            seriesId = parentBook.getSeriesId();
            if (seriesOrder == null) {
                Double maxOrder = writingRepository.findMaxSeriesOrder(seriesId);
                seriesOrder = (maxOrder != null ? maxOrder : 0.0) + 1.0;
            }
        }

        // ─── دروستکردنی ئینتیتی ─────────────────────────────────────────────────
        Writing writing = Writing.builder()
                .ckbCoverUrl(ckbCoverUrl)
                .kmrCoverUrl(kmrCoverUrl)
                .hoverCoverUrl(hoverCoverUrl)
                .topic(topic)
                .contentLanguages(new LinkedHashSet<>(safeLangs(request.getContentLanguages())))
                .writingTopic(request.getWritingTopic())
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

        Writing writing = writingRepository.findById(id)
                .orElseThrow(() -> {
                    // هەڵە: نووسراوەکە نەدۆزرایەوە
                    return new NotFoundException("writing.not_found", Map.of("id", id));
                });

        validate(request);

        // ─── وێنەی بەرگ ─────────────────────────────────────────────────────
        //   پێشەکی: ناردنی نوێ → لینکی داواکراو → هێشتنەوەی کۆن
        String ckbCoverUrl   = resolveUpdate(ckbCoverImage,   request.getCkbCoverUrl(),   writing.getCkbCoverUrl());
        String kmrCoverUrl   = resolveUpdate(kmrCoverImage,   request.getKmrCoverUrl(),   writing.getKmrCoverUrl());
        String hoverCoverUrl = resolveUpdate(hoverCoverImage, request.getHoverCoverUrl(), writing.getHoverCoverUrl());

        writing.setCkbCoverUrl(ckbCoverUrl);
        writing.setKmrCoverUrl(kmrCoverUrl);
        writing.setHoverCoverUrl(hoverCoverUrl);

        // ─── فایلی کتێب ───────────────────────────────────────────────────────
        String ckbFileUrl = uploadFile(ckbBookFile, "فایلی کتێبی CKB");
        String kmrFileUrl = uploadFile(kmrBookFile, "فایلی کتێبی KMR");

        // ─── بابەت ────────────────────────────────────────────────────────────
        if (Boolean.TRUE.equals(request.getClearTopic())) {
            writing.setTopic(null);
        } else {
            PublishmentTopic topic = resolveTopic(request.getTopicId(), request.getNewTopic());
            if (topic != null) writing.setTopic(topic);
        }

        // ─── خانە هاوبەشەکان ────────────────────────────────────────────────────
        if (request.getWritingTopic()       != null) writing.setWritingTopic(request.getWritingTopic());
        if (request.getPublishedByInstitute() != null) writing.setPublishedByInstitute(request.getPublishedByInstitute());

        // ─── زمان و ناوەڕۆک ─────────────────────────────────────────────────────
        if (request.getContentLanguages() != null && !request.getContentLanguages().isEmpty()) {
            writing.setContentLanguages(new LinkedHashSet<>(request.getContentLanguages()));
        }
        applyContent(writing, request, ckbFileUrl, kmrFileUrl);

        // ─── تاگ و کلیلەووشەکان ──────────────────────────────────────────────────
        replaceBilingualSets(writing, request);

        // ─── زنجیرە ───────────────────────────────────────────────────────────
        String oldSeriesId = writing.getSeriesId();
        if (request.getSeriesName() != null) writing.setSeriesName(request.getSeriesName());
        if (request.getSeriesOrder() != null) writing.setSeriesOrder(request.getSeriesOrder());
        if (request.getParentBookId() != null) {
            Writing newParent = writingRepository.findById(request.getParentBookId())
                    .orElseThrow(() -> {
                        // هەڵە: کتێبی باوکی نوێ نەدۆزرایەوە
                        return new NotFoundException("parent_book.not_found",
                                Map.of("parentBookId", request.getParentBookId()));
                    });
            writing.setParentBook(newParent);
            writing.setSeriesId(newParent.getSeriesId());
        }

        Writing updated = writingRepository.save(writing);

        // نوێکردنەوەی ژماری زنجیرە ئەگەر کتێب گواسترابێتەوە بۆ زنجیرەیەکی تر
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

        Writing writing = writingRepository.findById(id)
                .orElseThrow(() -> {
                    // هەڵە: نووسراوەکە نەدۆزرایەوە بۆ سڕینەوە
                    return new NotFoundException("writing.not_found", Map.of("id", id));
                });

        String seriesId = writing.getSeriesId();
        String title    = getCombinedTitle(writing);
        Long   writingId = writing.getId();

        // ── سەرەتا: تۆمارکردنی لۆگ کاتێک نووسراو هەر هەیە ─────────────────────
        WritingLog log = WritingLog.builder()
                .writing(null)          // بەقەست null — FK پاک دەکرێتەوە بۆ لۆگی سڕینەوە
                .writingId(writingId)   // ئایدی هەمیشە دابمەزرێت
                .action("DELETED")
                .actorId("system")
                .actorName("System")
                .details("نووسراو '" + title + "' سڕایەوە")
                .createdAt(LocalDateTime.now())
                .build();
        writingLogRepository.saveAndFlush(log);

        // ── دواتر: سڕینەوەی نووسراو ────────────────────────────────────────────
        writingRepository.delete(writing);
        writingRepository.flush();

        // ── کۆتایی: نوێکردنەوەی ژماری زنجیرە ───────────────────────────────────
        updateSeriesCount(seriesId);
    }

    // =========================================================================
    // خوێندنەوە — هەموو / بەپێی ئایدی
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<Response> getAllWritings(Pageable pageable) {
        return writingRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * هێنانی نووسراو بەپێی ئایدی
     *
     * @throws NotFoundException - "نووسراو نەدۆزرایەوە"
     */
    @Transactional(readOnly = true)
    public Response getWritingById(Long id) {
        Writing writing = writingRepository.findById(id)
                .orElseThrow(() -> {
                    // هەڵە: نووسراوەکە نەدۆزرایەوە
                    return new NotFoundException("writing.not_found", Map.of("id", id));
                });
        return mapToResponse(writing);
    }

    // =========================================================================
    // کردارەکانی زنجیرە (Series)
    // =========================================================================

    /**
     * لکاندنی کتێب بە زنجیرەیەک
     *
     * @throws NotFoundException - "نووسراو نەدۆزرایەوە" (کتێب یان باوک)
     */
    @Transactional
    public Response linkBookToSeries(LinkToSeriesRequest request) {
        Writing book = writingRepository.findById(request.getBookId())
                .orElseThrow(() -> {
                    // هەڵە: کتێب نەدۆزرایەوە
                    return new NotFoundException("writing.not_found",
                            Map.of("bookId", request.getBookId()));
                });
        Writing parent = writingRepository.findById(request.getParentBookId())
                .orElseThrow(() -> {
                    // هەڵە: کتێبی باوک نەدۆزرایەوە
                    return new NotFoundException("parent_book.not_found",
                            Map.of("parentBookId", request.getParentBookId()));
                });

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
     * هێنانی هەموو کتێبەکانی زنجیرەیەک
     *
     * @throws NotFoundException - "زنجیرە نەدۆزرایەوە" (ئەگەر بەتاڵ بێت)
     */
    @Transactional(readOnly = true)
    public SeriesResponse getSeriesBooks(String seriesId) {
        List<Writing> books = writingRepository.findBySeriesIdOrderBySeriesOrderAsc(seriesId);
        if (books.isEmpty()) {
            // هەڵە: هیچ کتێبێک نەدۆزرایەوە لەم زنجیرەیە
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

    /**
     * گەڕان بەپێی نووسەر
     *
     * @throws BadRequestException - "ناوی نووسەر پێویستە" (ئەگەر بەتاڵ بێت)
     */
    @Transactional(readOnly = true)
    public Page<Response> searchByWriter(String writerName, String language, Pageable pageable) {
        if (isBlank(writerName)) {
            // هەڵە: ناوی نووسەر پێویستە
            throw new BadRequestException("search.writer.required", Map.of("field", "writerName"));
        }
        String q = writerName.trim();
        Page<Writing> results;
        if ("ckb".equalsIgnoreCase(language)) {
            results = writingRepository.findByWriterCkbContainingIgnoreCase(q, pageable);
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = writingRepository.findByWriterKmrContainingIgnoreCase(q, pageable);
        } else {
            results = writingRepository.findByWriterInBothLanguages(q, pageable);
        }
        return results.map(this::mapToResponse);
    }

    /**
     * گەڕان بەپێی تاگ
     *
     * @throws BadRequestException - "تاگی گەڕان پێویستە"
     */
    @Transactional(readOnly = true)
    public Page<Response> searchByTag(String tag, String language, Pageable pageable) {
        if (isBlank(tag)) {
            // هەڵە: تاگی گەڕان پێویستە
            throw new BadRequestException("search.tag.required", Map.of("field", "tag"));
        }
        String q = tag.trim();
        Page<Writing> results;
        if ("ckb".equalsIgnoreCase(language))       results = writingRepository.findByTagCkb(q, pageable);
        else if ("kmr".equalsIgnoreCase(language))  results = writingRepository.findByTagKmr(q, pageable);
        else                                        results = writingRepository.findByTagInBothLanguages(q, pageable);
        return results.map(this::mapToResponse);
    }

    /**
     * گەڕان بەپێی کلیلەووشە
     *
     * @throws BadRequestException - "کلیلەووشەی گەڕان پێویستە"
     */
    @Transactional(readOnly = true)
    public Page<Response> searchByKeyword(String keyword, String language, Pageable pageable) {
        if (isBlank(keyword)) {
            // هەڵە: کلیلەووشەی گەڕان پێویستە
            throw new BadRequestException("search.keyword.required", Map.of("field", "keyword"));
        }
        String q = keyword.trim();
        Page<Writing> results;
        if ("ckb".equalsIgnoreCase(language))       results = writingRepository.findByKeywordCkb(q, pageable);
        else if ("kmr".equalsIgnoreCase(language))  results = writingRepository.findByKeywordKmr(q, pageable);
        else                                        results = writingRepository.findByKeywordInBothLanguages(q, pageable);
        return results.map(this::mapToResponse);
    }

    // =========================================================================
    // دۆزینەوەی بابەت
    // =========================================================================

    /**
     * دۆزینەوەی بابەت بۆ دروستکردن/نوێکردنەوە:
     *  ١. ئەگەر topicId هەبێت → دۆزینەوەی لە بنکەدراو
     *  ٢. ئەگەر newTopic هەبێت → دروستکردنی بابەتی نوێ
     *  ٣. ئەگەر هیچ نەبێت → null (بێ بابەت)
     *
     * @throws NotFoundException - "بابەت نەدۆزرایەوە"
     */
    private PublishmentTopic resolveTopic(Long topicId, TopicPayload newTopic) {
        if (topicId != null) {
            return topicRepository.findById(topicId)
                    .orElseThrow(() -> {
                        // هەڵە: بابەت نەدۆزرایەوە
                        return new NotFoundException("topic.not_found", Map.of("topicId", topicId));
                    });
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

    /**
     * پشتڕاستکردنەوەی دروستکردن
     *
     * @throws BadRequestException - "زمانەکانی ناوەڕۆک پێویستە"
     * @throws BadRequestException - "ناوەڕۆک کەمە"
     * @throws BadRequestException - "ناونیشانی نووسراو پێویستە"
     */
    private void validate(CreateRequest request, boolean isCreate) {
        if (request.getContentLanguages() == null || request.getContentLanguages().isEmpty()) {
            // هەڵە: دەبێت لانیکەم یەک زمان هەڵبژێردرێت
            throw new BadRequestException("writing.languages.required",
                    Map.of("field", "contentLanguages"));
        }
        for (Language lang : request.getContentLanguages()) {
            LanguageContentDto content = lang == Language.CKB ? request.getCkbContent() : request.getKmrContent();
            if (content == null) {
                // هەڵە: ناوەڕۆک بۆ ئەم زمانە دیاری نەکراوە
                throw new BadRequestException("writing.content.missing",
                        Map.of("language", lang, "message", "ناوەڕۆک بۆ " + lang + " دیاری نەکراوە"));
            }
            if (isCreate && isBlank(content.getTitle())) {
                // هەڵە: ناونیشان پێویستە بۆ دروستکردن
                throw new BadRequestException("writing.title.required",
                        Map.of("language", lang, "message", "ناونیشان بۆ " + lang + " پێویستە"));
            }
        }
    }

    /**
     * پشتڕاستکردنەوەی نوێکردنەوە
     *
     * @throws BadRequestException - "ناوەڕۆک کەمە"
     */
    private void validate(UpdateRequest request) {
        if (request.getContentLanguages() == null) return;
        for (Language lang : request.getContentLanguages()) {
            LanguageContentDto content = lang == Language.CKB ? request.getCkbContent() : request.getKmrContent();
            if (content == null) {
                throw new BadRequestException("writing.content.missing",
                        Map.of("language", lang));
            }
        }
    }

    // =========================================================================
    // ناوەڕۆک
    // =========================================================================

    /** دانانی ناوەڕۆکی زمانی لە CreateRequest — لینکی فایل لە DTO Override دەکات */
    private void applyContent(Writing writing, CreateRequest request,
                              String ckbFileUrl, String kmrFileUrl) {
        if (request.getContentLanguages().contains(Language.CKB)) {
            writing.setCkbContent(buildContent(request.getCkbContent(), ckbFileUrl));
        }
        if (request.getContentLanguages().contains(Language.KMR)) {
            writing.setKmrContent(buildContent(request.getKmrContent(), kmrFileUrl));
        }
    }

    /** دانانی ناوەڕۆکی زمانی لە UpdateRequest — تەنها خانە ناتەواوەکان نوێ دەکرێنەوە */
    private void applyContent(Writing writing, UpdateRequest request,
                              String ckbFileUrl, String kmrFileUrl) {
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
        if (dto.getTitle()       != null) existing.setTitle(trimOrNull(dto.getTitle()));
        if (dto.getDescription() != null) existing.setDescription(trimOrNull(dto.getDescription()));
        if (dto.getWriter()      != null) existing.setWriter(trimOrNull(dto.getWriter()));
        if (fileUrl              != null) existing.setFileUrl(fileUrl);
        else if (dto.getFileUrl() != null) existing.setFileUrl(trimOrNull(dto.getFileUrl()));
        if (dto.getFileFormat()  != null) existing.setFileFormat(dto.getFileFormat());
        if (dto.getFileSizeBytes() != null) existing.setFileSizeBytes(dto.getFileSizeBytes());
        if (dto.getPageCount()   != null) existing.setPageCount(dto.getPageCount());
        if (dto.getGenre()       != null) existing.setGenre(trimOrNull(dto.getGenre()));
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
                .contentLanguages(w.getContentLanguages() != null ? new LinkedHashSet<>(w.getContentLanguages()) : new LinkedHashSet<>())
                // ─── ٣ وێنەی بەرگ ────────────────────────────────────────────
                .ckbCoverUrl(w.getCkbCoverUrl())
                .kmrCoverUrl(w.getKmrCoverUrl())
                .hoverCoverUrl(w.getHoverCoverUrl())
                // ─── بابەت ────────────────────────────────────────────────────
                .topic(w.getTopic() != null
                        ? TopicInfo.builder()
                        .id(w.getTopic().getId())
                        .nameCkb(w.getTopic().getNameCkb())
                        .nameKmr(w.getTopic().getNameKmr())
                        .build()
                        : null)
                .writingTopic(w.getWritingTopic())
                .publishedByInstitute(w.isPublishedByInstitute())
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();

        // ─── ناوەڕۆکی CKB ──────────────────────────────────────────────────────
        if (w.getCkbContent() != null) {
            WritingContent c = w.getCkbContent();
            r.setCkbContent(LanguageContentDto.builder()
                    .title(c.getTitle())
                    .description(c.getDescription())
                    .writer(c.getWriter())
                    .fileUrl(c.getFileUrl())
                    .fileFormat(c.getFileFormat())
                    .fileSizeBytes(c.getFileSizeBytes())
                    .pageCount(c.getPageCount())
                    .genre(c.getGenre())
                    .build());
        }

        // ─── ناوەڕۆکی KMR ──────────────────────────────────────────────────────
        if (w.getKmrContent() != null) {
            WritingContent c = w.getKmrContent();
            r.setKmrContent(LanguageContentDto.builder()
                    .title(c.getTitle())
                    .description(c.getDescription())
                    .writer(c.getWriter())
                    .fileUrl(c.getFileUrl())
                    .fileFormat(c.getFileFormat())
                    .fileSizeBytes(c.getFileSizeBytes())
                    .pageCount(c.getPageCount())
                    .genre(c.getGenre())
                    .build());
        }

        // ─── تاگ و کلیلەووشەکان ──────────────────────────────────────────────────
        r.setTags(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(w.getTagsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(w.getTagsKmr())))
                .build());

        r.setKeywords(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(w.getKeywordsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(w.getKeywordsKmr())))
                .build());

        // ─── زانیاری زنجیرە ──────────────────────────────────────────────────────
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

    /**
     * ناردنی فایل بۆ S3 و گەڕاندنەوەی لینک
     * null دەگەڕێنێتەوە ئەگەر فایل بەتاڵ بێت
     *
     * @throws BadRequestException - "شکستی ناردنی فایل"
     */
    private String uploadFile(MultipartFile file, String description) {
        if (file == null || file.isEmpty()) return null;
        try {
            String url = s3Service.upload(file.getBytes(), file.getOriginalFilename(), file.getContentType());
            log.info("{} نێردرا → {}", description, url);
            return url;
        } catch (IOException e) {
            // هەڵە: کێشە لە ناردنی فایل
            throw new BadRequestException("media.upload.failed",
                    Map.of("description", description, "error", e.getMessage()));
        }
    }

    /**
     * ناردن ئەگەر فایل هەبێت؛ ئەگەر نەبێت گەڕاندنەوەی لینکی داواکراو
     */
    private String uploadOrFallback(MultipartFile file, String urlFallback, String description) {
        String uploaded = uploadFile(file, description);
        return uploaded != null ? uploaded : trimOrNull(urlFallback);
    }

    /**
     * دانانی نرخی کۆتایی بۆ وێنەی بەرگ لە کاتی نوێکردنەوە:
     *  ١. ناردنی نوێ → بەکارهێنانی لینکی نوێ
     *  ٢. لینکی دیاریکراو لە داواکاری → بەکارهێنانی (ڕێسای بەتاڵ = سڕینەوە)
     *  ٣. هیچیان نەبێت → هێشتنەوەی کۆن
     */
    private String resolveUpdate(MultipartFile file, String requestUrl, String existing) {
        String uploaded = uploadFile(file, "وێنەی بەرگ");
        if (uploaded != null) return uploaded;
        if (requestUrl != null) {
            String t = requestUrl.trim();
            return t.isEmpty() ? null : t;   // ڕیزبەندی بەتاڵ = پاککردنەوە
        }
        return existing;
    }

    // =========================================================================
    // تۆمارکردنی چالاکی (Audit Log)
    // =========================================================================

    private void logAction(Writing writing, String action, String details) {
        writingLogRepository.save(WritingLog.builder()
                .writing(writing)
                .writingId(writing.getId())   // denormalised — هەمیشە دابمەزرێت
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

    private boolean isBlank(String s)   { return s == null || s.isBlank(); }
    private String trimOrNull(String s)  { if (s == null) return null; String t = s.trim(); return t.isEmpty() ? null : t; }
    private Set<Language> safeLangs(Set<Language> l) { return l == null ? Set.of() : l; }
    private <T> Set<T> safeSet(Set<T> s)             { return s == null ? Set.of() : s; }
    private Set<String> cleanStrings(Set<String> in) {
        if (in == null || in.isEmpty()) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : in) { if (s != null && !s.isBlank()) out.add(s.trim()); }
        return out;
    }
}