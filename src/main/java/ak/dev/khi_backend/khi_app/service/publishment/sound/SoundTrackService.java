package ak.dev.khi_backend.khi_app.service.publishment.sound;

import ak.dev.khi_backend.khi_app.dto.publishment.sound.SoundTrackDtos.*;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.FileType;
import ak.dev.khi_backend.khi_app.enums.publishment.TrackState;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.exceptions.NotFoundException;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrack;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrackContent;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrackFile;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrackLog;
import ak.dev.khi_backend.khi_app.repository.publishment.topic.PublishmentTopicRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.sound.SoundTrackLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.sound.SoundTrackRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * سێرڤیسی تڕاکی دەنگ - بەڕێوەبردنی تۆماری دەنگ و ئۆدیۆ
 *
 * لیستی هەڵە کوردیەکان کە بەکاردێن:
 *
 * ١. "request.required" = "داواکاری پێویستە: زانیاری نەنێردراوە" (Bad Request)
 * ٢. "soundtrack.not_found" = "تڕاکی دەنگ نەدۆزرایەوە: ئایدییەکە بوونی نییە" (Not Found)
 * ٣. "soundtrack.soundType.required" = "جۆری دەنگ پێویستە: دەبێت جۆری تڕاک دیاری بکرێت" (Bad Request)
 * ٤. "soundtrack.soundType.blank" = "جۆری دەنگ بەتاڵە: ناتوانرێت بەتاڵە بێت" (Bad Request)
 * ٥. "soundtrack.languages.required" = "زمانەکانی ناوەڕۆک پێویستە: دەبێت لانیکەم یەک زمان هەڵبژێردرێت" (Bad Request)
 * ٦. "soundtrack.ckb.title.required" = "ناونیشانی کوردیی ناوەندی پێویستە: کاتێک CKB چالاکە دەبێت ناونیشان هەبێت" (Bad Request)
 * ٧. "soundtrack.kmr.title.required" = "ناونیشانی کوردیی باکووری پێویستە: کاتێک KMR چالاکە دەبێت ناونیشان هەبێت" (Bad Request)
 * ٨. "soundtrack.single.invalid" = "هەڵەی جۆری تاک: تڕاکی SINGLE تەنها یەک سەرچاوەی دەنگی دەبێت" (Bad Request)
 * ٩. "soundtrack.file.source.required" = "سەرچاوەی فایل پێویستە: دەبێت لینکی ڕاستەقینە یان دەرەکی یان ئێمبێد هەبێت" (Bad Request)
 * ١٠. "media.upload.failed" = "شکستی ناردنی فایل: کێشە لە ناردنی فایل بۆ S3" (Bad Request)
 * ١١. "topic.not_found" = "بابەت نەدۆزرایەوە: ئایدیی بابەت بوونی نییە" (Not Found)
 * ١٢. "topic.type.mismatch" = "جۆری بابەت هەڵەیە: بابەتەکە بۆ SOUND نییە" (Bad Request)
 * ١٣. "topic.names.required" = "ناوی بابەت پێویستە: دەبێت لانیکەم ناوێکی کوردی بنووسرێت" (Bad Request)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoundTrackService {

    private static final String TOPIC_ENTITY_TYPE = "SOUND";

    private final SoundTrackRepository       soundTrackRepository;
    private final SoundTrackLogRepository    soundTrackLogRepository;
    private final PublishmentTopicRepository topicRepository;
    private final S3Service                  s3Service;
    private final ObjectMapper               objectMapper;

    // ═══════════════════════════════════════════════════════════════════════════
    // دروستکردن
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * دروستکردنی تڕاکی دەنگی نوێ
     *
     * @throws BadRequestException - داواکاری بەتاڵە ("داواکاری پێویستە")
     * @throws BadRequestException - جۆری دەنگ بەتاڵە ("جۆری دەنگ پێویستە")
     * @throws BadRequestException - زمانەکان بەتاڵە ("زمانەکانی ناوەڕۆک پێویستە")
     * @throws BadRequestException - ناونیشانی کوردی پێویستە
     * @throws BadRequestException - SINGLE بە چەند فایل ("هەڵەی جۆری تاک")
     * @throws BadRequestException - کێشە لە ناردنی فایل ("شکستی ناردنی فایل")
     * @throws NotFoundException   - بابەت نەدۆزرایەوە ("بابەت نەدۆزرایەوە")
     * @throws BadRequestException - جۆری بابەت هەڵەیە ("جۆری بابەت هەڵەیە")
     */
    @Transactional
    public Response addSoundTrack(CreateRequest request, List<MultipartFile> audioFiles, MultipartFile ckbCoverImage , MultipartFile kmrCoverImage ,MultipartFile hoverImage) {

        int uploadedCount = countFiles(audioFiles);
        int linksCount    = request.getFiles() == null ? 0 :
                (int) request.getFiles().stream().filter(Objects::nonNull).count();

        log.info("زیادکردنی تڕاکی دەنگ: {} | فایلە نێردراوەکان={} | لینکەکان={}",
                getCombinedTitle(request), uploadedCount, linksCount);

        validate(request);

        if (request.getTrackState() == TrackState.SINGLE && (uploadedCount + linksCount) > 1) {
            // هەڵە: تڕاکی تاک پێویستی بە تەنها یەک سەرچاوەی دەنگی هەیە
            throw new BadRequestException("soundtrack.single.invalid",
                    Map.of("message", "تڕاکی SINGLE تەنها دەتوانێت یەک سەرچاوەی دەنگی هەبێت",
                            "uploaded", uploadedCount, "links", linksCount));
        }

        boolean albumOfMemories = request.getTrackState() == TrackState.MULTI
                && Boolean.TRUE.equals(request.getAlbumOfMemories());

        String ckbCover = uploadCoverIfPresent(ckbCoverImage);
        String kmrCover = uploadCoverIfPresent(kmrCoverImage);
        String hoverCover  = uploadCoverIfPresent(hoverImage);

        // ── بابەت: inline یان بە ئایدی ─────────────────────────────────────────
        PublishmentTopic topic = resolveOrCreateTopic(request.getTopicId(), request.getNewTopic());

        SoundTrack soundTrack = SoundTrack.builder()
                .ckbCoverUrl(ckbCover)
                .kmrCoverUrl(kmrCover)
                .hoverCoverUrl(hoverCover)
                .soundType(trimOrNull(request.getSoundType()))
                .trackState(request.getTrackState())
                .albumOfMemories(albumOfMemories)
                .topic(topic)
                .contentLanguages(new LinkedHashSet<>(safeLangs(request.getContentLanguages())))
                .locations(new LinkedHashSet<>(safeSet(request.getLocations())))
                .director(trimOrNull(request.getDirector()))
                .thisProjectOfInstitute(request.isThisProjectOfInstitute())
                .tagsCkb(new LinkedHashSet<>(safeSet(request.getTags() != null ? request.getTags().getCkb() : null)))
                .tagsKmr(new LinkedHashSet<>(safeSet(request.getTags() != null ? request.getTags().getKmr() : null)))
                .keywordsCkb(new LinkedHashSet<>(safeSet(request.getKeywords() != null ? request.getKeywords().getCkb() : null)))
                .keywordsKmr(new LinkedHashSet<>(safeSet(request.getKeywords() != null ? request.getKeywords().getKmr() : null)))
                .files(new ArrayList<>())
                .build();

        applyContentByLanguages(soundTrack, request);
        attachUploadedAudioFiles(soundTrack, audioFiles, request.getReaderNames());
        attachFilesFromDto(soundTrack, request.getFiles());

        SoundTrack saved = soundTrackRepository.save(soundTrack);
        logAction(saved, "CREATED",
                String.format("تڕاکی دەنگ '%s' دروستکرا لەگەڵ %d فایل", getCombinedTitle(saved), saved.getFiles().size()));

        return mapToResponse(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // نوێکردنەوە
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * نوێکردنەوەی تڕاکی دەنگ
     *
     * @throws BadRequestException    - ئایدی بەتاڵە
     * @throws NotFoundException      - "تڕاکی دەنگ نەدۆزرایەوە"
     * @throws BadRequestException    - "جۆری دەنگ بەتاڵە"
     * @throws BadRequestException    - "هەڵەی جۆری تاک" (ئەگەر SINGLE بێت و فایل زۆر بێت)
     * @throws BadRequestException    - "شکستی ناردنی فایل"
     */
    @Transactional
    public Response updateSoundTrack(Long id, UpdateRequest request, List<MultipartFile> audioFiles, MultipartFile ckbCoverImage , MultipartFile kmrCoverImage ,MultipartFile hoverImage) {
        log.info("نوێکردنەوەی تڕاکی دەنگ id={}", id);

        if (id == null) {
            throw new BadRequestException("error.validation", Map.of("field", "id", "message", "ئایدیی تڕاک پێویستە"));
        }

        SoundTrack soundTrack = soundTrackRepository.findById(id)
                .orElseThrow(() -> {
                    // هەڵە: تڕاکەکە نەدۆزرایەوە
                    return new NotFoundException("soundtrack.not_found", Map.of("id", id));
                });

        validateUpdate(request);
        Map<String, Object> changes = new HashMap<>();

        // وێنەی بەرگی CKB
        if (ckbCoverImage != null && !ckbCoverImage.isEmpty()) {
            soundTrack.setCkbCoverUrl(uploadCoverIfPresent(ckbCoverImage));
            changes.put("ckbCoverUrl", "نوێکرایەوە");
        }

        // وێنەی بەرگی KMR
        if (kmrCoverImage != null && !kmrCoverImage.isEmpty()) {
            soundTrack.setKmrCoverUrl(uploadCoverIfPresent(kmrCoverImage));
            changes.put("kmrCoverUrl", "نوێکرایەوە");
        }

        if (hoverImage != null && !hoverImage.isEmpty()) {
            soundTrack.setHoverCoverUrl(uploadCoverIfPresent(hoverImage));
            changes.put("hoverCoverUrl", "نوێکرایەوە");
        }

        // جۆری دەنگ
        if (request.getSoundType() != null) {
            String newType = trimOrNull(request.getSoundType());
            if (isBlank(newType)) {
                // هەڵە: جۆری دەنگ ناتوانرێت بەتاڵە بێت
                throw new BadRequestException("soundtrack.soundType.blank", Map.of("field", "soundType"));
            }
            if (!Objects.equals(newType, soundTrack.getSoundType())) {
                changes.put("soundType", Map.of("کۆن", soundTrack.getSoundType(), "نوێ", newType));
                soundTrack.setSoundType(newType);
            }
        }

        // بابەت: clearTopic > topicId > newTopic
        if (request.isClearTopic()) {
            soundTrack.setTopic(null);
            changes.put("topic", "سڕایەوە");
        } else if (request.getTopicId() != null || request.getNewTopic() != null) {
            soundTrack.setTopic(resolveOrCreateTopic(request.getTopicId(), request.getNewTopic()));
            changes.put("topic", request.getTopicId() != null
                    ? "topicId=" + request.getTopicId() : "دروستکرا-inline");
        }

        // دۆخی تڕاک ( SINGLE یان MULTI )
        if (request.getTrackState() != null && request.getTrackState() != soundTrack.getTrackState()) {
            changes.put("trackState", Map.of("کۆن", soundTrack.getTrackState(), "نوێ", request.getTrackState()));
            soundTrack.setTrackState(request.getTrackState());
            if (request.getTrackState() == TrackState.SINGLE) {
                soundTrack.setAlbumOfMemories(false);
            }
        }

        // ئەلبومی بیرەوەریەکان
        if (request.getAlbumOfMemories() != null) {
            TrackState effectiveState = soundTrack.getTrackState();
            boolean newFlag = effectiveState == TrackState.MULTI
                    && Boolean.TRUE.equals(request.getAlbumOfMemories());
            if (newFlag != soundTrack.isAlbumOfMemories()) {
                changes.put("albumOfMemories", Map.of("کۆن", soundTrack.isAlbumOfMemories(), "نوێ", newFlag));
                soundTrack.setAlbumOfMemories(newFlag);
            }
        }

        if (request.getLocations() != null)
            soundTrack.setLocations(new LinkedHashSet<>(cleanStrings(request.getLocations())));
        if (request.getDirector() != null)
            soundTrack.setDirector(trimOrNull(request.getDirector()));
        if (request.getThisProjectOfInstitute() != null)
            soundTrack.setThisProjectOfInstitute(request.getThisProjectOfInstitute());
        if (request.getContentLanguages() != null)
            soundTrack.setContentLanguages(new LinkedHashSet<>(request.getContentLanguages()));

        applyContentByLanguagesUpdate(soundTrack, request);
        replaceBilingualSets(soundTrack, request);

        // گۆڕینی فایلەکان
        boolean hasUploads = audioFiles != null && audioFiles.stream().anyMatch(f -> f != null && !f.isEmpty());
        boolean hasLinks   = request.getFiles() != null;

        if (hasUploads || hasLinks) {
            int uploadedCount = hasUploads ? countFiles(audioFiles) : 0;
            int linksCount    = hasLinks ? (int) request.getFiles().stream().filter(Objects::nonNull).count() : 0;

            if (soundTrack.getTrackState() == TrackState.SINGLE && (uploadedCount + linksCount) > 1) {
                // هەڵە: تڕاکی SINGLE تەنها یەک فایل دەبێت
                throw new BadRequestException("soundtrack.single.invalid",
                        Map.of("message", "تڕاکی SINGLE تەنها یەک سەرچاوەی دەنگی دەبێت"));
            }

            soundTrack.getFiles().clear();
            if (hasUploads) {
                attachUploadedAudioFiles(soundTrack, audioFiles, request.getReaderNames());
                changes.put("files", "گۆڕدرا بە نێردراو: " + uploadedCount);
            }
            attachFilesFromDto(soundTrack, request.getFiles());
            if (hasLinks) changes.put("links", "لینکەکان نوێکرانەوە");
        }

        SoundTrack updated = soundTrackRepository.save(soundTrack);

        if (!changes.isEmpty()) {
            try {
                logAction(updated, "UPDATED", objectMapper.writeValueAsString(changes));
            } catch (Exception e) {
                logAction(updated, "UPDATED", "تڕاکی دەنگ نوێکرایەوە");
            }
        }

        return mapToResponse(updated);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // هێنانەوە
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Response> getAllSoundTracks() {
        return soundTrackRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Response> getAlbumsOfMemories() {
        return soundTrackRepository.findAlbumsOfMemories().stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Response> getRegularMultiTracks() {
        return soundTrackRepository.findRegularMultiTracks().stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // سڕینەوە
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * سڕینەوەی تڕاکی دەنگ
     *
     * @throws NotFoundException - "تڕاکی دەنگ نەدۆزرایەوە"
     */
    @Transactional
    public void deleteSoundTrack(Long id) {
        SoundTrack soundTrack = soundTrackRepository.findById(id)
                .orElseThrow(() -> {
                    // هەڵە: تڕاکەکە نەدۆزرایەوە بۆ سڕینەوە
                    return new NotFoundException("soundtrack.not_found", Map.of("id", id));
                });
        String title = getCombinedTitle(soundTrack);
        detachLogsBeforeHardDelete(soundTrack);
        logDeleteAction(soundTrack.getId(), title, "تڕاکی دەنگ '" + title + "' سڕایەوە");
        soundTrackRepository.delete(soundTrack);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // گەڕان
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Response> searchByTag(String tag, String language) {
        if (isBlank(tag)) {
            // هەڵە: تاگی گەڕان بەتاڵە
            throw new BadRequestException("tag.required", Map.of("message", "تاگی گەڕان پێویستە"));
        }
        String t = tag.trim();
        return soundTrackRepository.findAll().stream()
                .filter(s -> {
                    if ("ckb".equalsIgnoreCase(language))
                        return s.getTagsCkb().stream().anyMatch(x -> x.equalsIgnoreCase(t));
                    if ("kmr".equalsIgnoreCase(language))
                        return s.getTagsKmr().stream().anyMatch(x -> x.equalsIgnoreCase(t));
                    return s.getTagsCkb().stream().anyMatch(x -> x.equalsIgnoreCase(t))
                            || s.getTagsKmr().stream().anyMatch(x -> x.equalsIgnoreCase(t));
                })
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Response> searchByKeyword(String keyword, String language) {
        if (isBlank(keyword)) {
            // هەڵە: کلیلەووشەی گەڕان بەتاڵە
            throw new BadRequestException("keyword.required", Map.of("message", "کلیلەووشەی گەڕان پێویستە"));
        }
        String kw = keyword.trim();
        return soundTrackRepository.findAll().stream()
                .filter(s -> {
                    if ("ckb".equalsIgnoreCase(language))
                        return s.getKeywordsCkb().stream().anyMatch(k -> k.equalsIgnoreCase(kw));
                    if ("kmr".equalsIgnoreCase(language))
                        return s.getKeywordsKmr().stream().anyMatch(k -> k.equalsIgnoreCase(kw));
                    return s.getKeywordsCkb().stream().anyMatch(k -> k.equalsIgnoreCase(kw))
                            || s.getKeywordsKmr().stream().anyMatch(k -> k.equalsIgnoreCase(kw));
                })
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Response> searchByLocation(String location) {
        if (isBlank(location)) {
            throw new BadRequestException("location.required", Map.of("message", "شوێنی گەڕان پێویستە"));
        }
        return soundTrackRepository.findAll().stream()
                .filter(s -> s.getLocations().stream().anyMatch(loc -> loc.equalsIgnoreCase(location.trim())))
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // یاریدەدەرەکانی بابەت
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * ڕیزبەندی چارەسەرکردن:
     *  ١. topicId هەبێت  → دۆزینەوەی بابەتی بەرەمە
     *  ٢. newTopic هەبێت → دروستکردنی بابەتی نوێ
     *  ٣. هیچیان نەبێت   → null
     *
     * @throws NotFoundException   - "بابەت نەدۆزرایەوە"
     * @throws BadRequestException - "جۆری بابەت هەڵەیە"
     * @throws BadRequestException - "ناوی بابەت پێویستە"
     */
    private PublishmentTopic resolveOrCreateTopic(Long topicId, InlineTopicRequest newTopic) {
        if (topicId != null) {
            return findTopicOrThrow(topicId);
        }
        if (newTopic != null) {
            if (isBlank(newTopic.getNameCkb()) && isBlank(newTopic.getNameKmr())) {
                // هەڵە: دەبێت لانیکەم ناوێکی کوردی بنووسرێت
                throw new BadRequestException("topic.names.required",
                        Map.of("message", "بابەتی نوێ پێویستی بە لانیکەم ناوێکی کوردییە"));
            }
            PublishmentTopic created = topicRepository.save(PublishmentTopic.builder()
                    .entityType(TOPIC_ENTITY_TYPE)
                    .nameCkb(trimOrNull(newTopic.getNameCkb()))
                    .nameKmr(trimOrNull(newTopic.getNameKmr()))
                    .build());
            log.info("بابەتی SOUND دروستکرا inline id={}", created.getId());
            return created;
        }
        return null;
    }

    private PublishmentTopic findTopicOrThrow(Long topicId) {
        PublishmentTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> {
                    // هەڵە: بابەتەکە نەدۆزرایەوە
                    return new NotFoundException("topic.not_found", Map.of("id", topicId));
                });
        if (!TOPIC_ENTITY_TYPE.equals(topic.getEntityType())) {
            // هەڵە: بابەتەکە بۆ SOUND نییە
            throw new BadRequestException("topic.type.mismatch",
                    Map.of("message", "بابەت id=" + topicId + " بۆ '" + topic.getEntityType() +
                            "'ە، چاوەڕوان دەکرێت 'SOUND' بێت"));
        }
        return topic;
    }

    private TopicView toTopicView(PublishmentTopic t) {
        return TopicView.builder()
                .id(t.getId())
                .nameCkb(t.getNameCkb())
                .nameKmr(t.getNameKmr())
                .createdAt(t.getCreatedAt())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // یاریدەدەرەکانی ناوەڕۆک و زمان
    // ═══════════════════════════════════════════════════════════════════════════

    private void applyContentByLanguages(SoundTrack soundTrack, CreateRequest request) {
        Set<Language> langs = safeLangs(soundTrack.getContentLanguages());
        if (langs.contains(Language.CKB)) {
            soundTrack.setCkbContent(buildContent(request.getCkbContent()));
        } else {
            soundTrack.setCkbContent(null);
            soundTrack.getTagsCkb().clear();
            soundTrack.getKeywordsCkb().clear();
        }
        if (langs.contains(Language.KMR)) {
            soundTrack.setKmrContent(buildContent(request.getKmrContent()));
        } else {
            soundTrack.setKmrContent(null);
            soundTrack.getTagsKmr().clear();
            soundTrack.getKeywordsKmr().clear();
        }
    }

    private void applyContentByLanguagesUpdate(SoundTrack soundTrack, UpdateRequest request) {
        Set<Language> langs = safeLangs(soundTrack.getContentLanguages());
        if (langs.contains(Language.CKB)) {
            if (request.getCkbContent() != null) soundTrack.setCkbContent(buildContent(request.getCkbContent()));
        } else {
            soundTrack.setCkbContent(null);
            soundTrack.getTagsCkb().clear();
            soundTrack.getKeywordsCkb().clear();
        }
        if (langs.contains(Language.KMR)) {
            if (request.getKmrContent() != null) soundTrack.setKmrContent(buildContent(request.getKmrContent()));
        } else {
            soundTrack.setKmrContent(null);
            soundTrack.getTagsKmr().clear();
            soundTrack.getKeywordsKmr().clear();
        }
    }

    private SoundTrackContent buildContent(LanguageContentDto dto) {
        if (dto == null) return null;
        if (isBlank(dto.getTitle()) && isBlank(dto.getDescription()) && isBlank(dto.getReading())) return null;
        return SoundTrackContent.builder()
                .title(trimOrNull(dto.getTitle()))
                .description(trimOrNull(dto.getDescription()))
                .reading(trimOrNull(dto.getReading()))
                .build();
    }

    private void replaceBilingualSets(SoundTrack soundTrack, UpdateRequest request) {
        if (request.getTags() != null) {
            if (request.getTags().getCkb() != null) {
                soundTrack.getTagsCkb().clear();
                soundTrack.getTagsCkb().addAll(cleanStrings(request.getTags().getCkb()));
            }
            if (request.getTags().getKmr() != null) {
                soundTrack.getTagsKmr().clear();
                soundTrack.getTagsKmr().addAll(cleanStrings(request.getTags().getKmr()));
            }
        }
        if (request.getKeywords() != null) {
            if (request.getKeywords().getCkb() != null) {
                soundTrack.getKeywordsCkb().clear();
                soundTrack.getKeywordsCkb().addAll(cleanStrings(request.getKeywords().getCkb()));
            }
            if (request.getKeywords().getKmr() != null) {
                soundTrack.getKeywordsKmr().clear();
                soundTrack.getKeywordsKmr().addAll(cleanStrings(request.getKeywords().getKmr()));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // یاریدەدەرەکانی فایل
    // ═══════════════════════════════════════════════════════════════════════════

    private void attachUploadedAudioFiles(SoundTrack soundTrack, List<MultipartFile> audioFiles, List<String> readerNames) {
        if (audioFiles == null || audioFiles.isEmpty()) return;
        int i = 0;
        for (MultipartFile audioFile : audioFiles) {
            if (audioFile == null || audioFile.isEmpty()) continue;
            String readerName = (readerNames != null && i < readerNames.size()) ? readerNames.get(i) : null;
            try {
                String audioUrl = s3Service.upload(audioFile.getBytes(), audioFile.getOriginalFilename(), audioFile.getContentType());
                soundTrack.addFile(SoundTrackFile.builder()
                        .fileUrl(audioUrl)
                        .fileType(determineFileType(audioFile))
                        .durationSeconds(0)
                        .sizeBytes(audioFile.getSize())
                        .readerName(readerName)
                        .build());
                i++;
            } catch (IOException e) {
                // هەڵە: شکستی ناردنی ئۆدیۆ
                throw new BadRequestException("media.upload.failed",
                        Map.of("filename", audioFile.getOriginalFilename(), "error", e.getMessage()));
            }
        }
    }

    /**
     * لکاندنی فایلەکان لە DTO
     *
     * @throws BadRequestException - "سەرچاوەی فایل پێویستە" (ئەگەر هیچ لینکێک نەبێت)
     */
    private void attachFilesFromDto(SoundTrack soundTrack, List<FileCreateRequest> files) {
        if (files == null || files.isEmpty()) return;
        Set<String> existing = new HashSet<>();
        if (soundTrack.getFiles() != null)
            soundTrack.getFiles().forEach(f -> { String k = fileKey(f); if (k != null) existing.add(k); });

        for (FileCreateRequest f : files) {
            if (f == null) continue;
            String fileUrl     = trimOrNull(f.getFileUrl());
            String externalUrl = trimOrNull(f.getExternalUrl());
            String embedUrl    = trimOrNull(f.getEmbedUrl());
            if (isBlank(fileUrl) && isBlank(externalUrl) && isBlank(embedUrl)) {
                // هەڵە: دەبێت لانیکەم یەک سەرچاوە دیاری بکرێت
                throw new BadRequestException("soundtrack.file.source.required",
                        Map.of("message", "هەر فایلێک پێویستی بە لینکی ڕاستەقینە یان دەرەکی یان ئێمبێد هەیە"));
            }

            FileType fileType = f.getFileType() != null ? f.getFileType() : FileType.OTHER;
            String key = fileKey(fileType, fileUrl, embedUrl, externalUrl);
            if (key == null || existing.contains(key)) continue;

            soundTrack.addFile(SoundTrackFile.builder()
                    .fileUrl(fileUrl)
                    .externalUrl(externalUrl)
                    .embedUrl(embedUrl)
                    .fileType(fileType)
                    .durationSeconds(f.getDurationSeconds() != null ? Math.max(0, f.getDurationSeconds()) : 0)
                    .sizeBytes(f.getSizeBytes() != null ? Math.max(0, f.getSizeBytes()) : 0)
                    .readerName(trimOrNull(f.getReaderName()))
                    .build());
            existing.add(key);
        }
    }

    private String fileKey(SoundTrackFile f) {
        return fileKey(f.getFileType(), f.getFileUrl(), f.getEmbedUrl(), f.getExternalUrl());
    }

    private String fileKey(FileType type, String fileUrl, String embedUrl, String externalUrl) {
        String best = trimOrNull(fileUrl);
        if (best == null) best = trimOrNull(embedUrl);
        if (best == null) best = trimOrNull(externalUrl);
        if (best == null) return null;
        return (type != null ? type.name() : "OTHER") + "|" + best;
    }

    private String uploadCoverIfPresent(MultipartFile coverImage) {
        if (coverImage == null || coverImage.isEmpty()) return null;
        try {
            return s3Service.upload(coverImage.getBytes(), coverImage.getOriginalFilename(), coverImage.getContentType());
        } catch (IOException e) {
            // هەڵە: شکستی ناردنی وێنەی بەرگ
            throw new BadRequestException("media.upload.failed",
                    Map.of("type", "وێنەی بەرگ", "error", e.getMessage()));
        }
    }

    private FileType determineFileType(MultipartFile file) {
        String ct = file.getContentType();
        String fn = file.getOriginalFilename();
        if (ct != null) {
            if (ct.contains("mp3") || (fn != null && fn.endsWith(".mp3"))) return FileType.MP3;
            if (ct.contains("wav") || (fn != null && fn.endsWith(".wav"))) return FileType.WAV;
            if (ct.contains("ogg") || (fn != null && fn.endsWith(".ogg"))) return FileType.OGG;
            if (ct.contains("aac") || (fn != null && fn.endsWith(".aac"))) return FileType.AAC;
            if (ct.contains("flac") || (fn != null && fn.endsWith(".flac"))) return FileType.FLAC;
        }
        return FileType.OTHER;
    }

    private int countFiles(List<MultipartFile> files) {
        if (files == null) return 0;
        return (int) files.stream().filter(f -> f != null && !f.isEmpty()).count();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // گۆڕین بۆ Response
    // ═══════════════════════════════════════════════════════════════════════════

    private Response mapToResponse(SoundTrack s) {
        List<FileResponse> fileDTOs = s.getFiles().stream()
                .map(f -> FileResponse.builder()
                        .id(f.getId())
                        .fileUrl(f.getFileUrl())
                        .externalUrl(f.getExternalUrl())
                        .embedUrl(f.getEmbedUrl())
                        .fileType(f.getFileType())
                        .durationSeconds(f.getDurationSeconds())
                        .sizeBytes(f.getSizeBytes())
                        .readerName(f.getReaderName())
                        .build())
                .collect(Collectors.toList());

        Response response = Response.builder()
                .id(s.getId())
                .ckbCoverUrl(s.getCkbCoverUrl())
                .kmrCoverUrl(s.getKmrCoverUrl())
                .hoverCoverUrl(s.getHoverCoverUrl())
                .soundType(s.getSoundType())
                .trackState(s.getTrackState())
                .albumOfMemories(s.isAlbumOfMemories())
                .topicId(s.getTopic() != null ? s.getTopic().getId() : null)
                .topicNameCkb(s.getTopic() != null ? s.getTopic().getNameCkb() : null)
                .topicNameKmr(s.getTopic() != null ? s.getTopic().getNameKmr() : null)
                .contentLanguages(s.getContentLanguages() != null
                        ? new LinkedHashSet<>(s.getContentLanguages()) : new LinkedHashSet<>())
                .locations(s.getLocations())
                .director(s.getDirector())
                .thisProjectOfInstitute(s.isThisProjectOfInstitute())
                .files(fileDTOs)
                .totalDurationSeconds(s.getFiles().stream().mapToLong(SoundTrackFile::getDurationSeconds).sum())
                .totalSizeBytes(s.getFiles().stream().mapToLong(SoundTrackFile::getSizeBytes).sum())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();

        if (s.getCkbContent() != null) {
            response.setCkbContent(LanguageContentDto.builder()
                    .title(s.getCkbContent().getTitle())
                    .description(s.getCkbContent().getDescription())
                    .reading(s.getCkbContent().getReading())
                    .build());
        }
        if (s.getKmrContent() != null) {
            response.setKmrContent(LanguageContentDto.builder()
                    .title(s.getKmrContent().getTitle())
                    .description(s.getKmrContent().getDescription())
                    .reading(s.getKmrContent().getReading())
                    .build());
        }

        response.setTags(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(s.getTagsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(s.getTagsKmr())))
                .build());
        response.setKeywords(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(s.getKeywordsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(s.getKeywordsKmr())))
                .build());

        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // پشتڕاستکردنەوە
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * پشتڕاستکردنەوەی دروستکردن
     *
     * @throws BadRequestException - "داواکاری پێویستە"
     * @throws BadRequestException - "جۆری دەنگ پێویستە"
     * @throws BadRequestException - "زمانەکانی ناوەڕۆک پێویستە"
     * @throws BadRequestException - "ناونیشانی کوردیی ناوەندی پێویستە"
     * @throws BadRequestException - "ناونیشانی کوردیی باکووری پێویستە"
     */
    private void validate(CreateRequest request) {
        if (request == null)
            // هەڵە: داواکاری بەتاڵە
            throw new BadRequestException("request.required", Map.of("field", "request"));
        if (isBlank(request.getSoundType()))
            // هەڵە: جۆری دەنگ پێویستە
            throw new BadRequestException("soundtrack.soundType.required", Map.of("field", "soundType"));

        Set<Language> langs = safeLangs(request.getContentLanguages());
        if (langs.isEmpty())
            // هەڵە: دەبێت لانیکەم یەک زمان هەڵبژێردرێت
            throw new BadRequestException("soundtrack.languages.required", Map.of("field", "contentLanguages"));

        if (langs.contains(Language.CKB) && (request.getCkbContent() == null || isBlank(request.getCkbContent().getTitle())))
            // هەڵە: ناونیشان بە کوردیی ناوەندی پێویستە
            throw new BadRequestException("soundtrack.ckb.title.required", Map.of("field", "ckbContent.title"));

        if (langs.contains(Language.KMR) && (request.getKmrContent() == null || isBlank(request.getKmrContent().getTitle())))
            // هەڵە: ناونیشان بە کوردیی باکووری پێویستە
            throw new BadRequestException("soundtrack.kmr.title.required", Map.of("field", "kmrContent.title"));
    }

    private void validateUpdate(UpdateRequest request) {
        if (request == null)
            // هەڵە: داواکاری بەتاڵە
            throw new BadRequestException("request.required", Map.of("field", "request"));
        if (request.getSoundType() != null && isBlank(request.getSoundType()))
            // هەڵە: جۆری دەنگ ناتوانرێت بەتاڵە بێت
            throw new BadRequestException("soundtrack.soundType.blank", Map.of("field", "soundType"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // تۆمارکردن
    // ═══════════════════════════════════════════════════════════════════════════

    private void logAction(SoundTrack soundTrack, String action, String details) {
        soundTrackLogRepository.save(SoundTrackLog.builder()
                .soundTrack(soundTrack)
                .soundTrackRefId(soundTrack.getId())
                .soundTrackTitle(getCombinedTitle(soundTrack))
                .action(action)
                .actorId("system").actorName("System")
                .details(details)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void logDeleteAction(Long id, String title, String details) {
        soundTrackLogRepository.save(SoundTrackLog.builder()
                .soundTrack(null)
                .soundTrackRefId(id)
                .soundTrackTitle(title)
                .action("DELETED")
                .actorId("system").actorName("System")
                .details(details)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void detachLogsBeforeHardDelete(SoundTrack soundTrack) {
        Long id = soundTrack.getId();
        String title = getCombinedTitle(soundTrack);
        List<SoundTrackLog> logs = soundTrackLogRepository.findBySoundTrackId(id);
        if (logs == null || logs.isEmpty()) return;
        for (SoundTrackLog l : logs) {
            if (l.getSoundTrackRefId() == null) l.setSoundTrackRefId(id);
            if (isBlank(l.getSoundTrackTitle())) l.setSoundTrackTitle(title);
            l.setSoundTrack(null);
        }
        soundTrackLogRepository.saveAll(logs);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // یاریدەدەرەکان
    // ═══════════════════════════════════════════════════════════════════════════

    private String getCombinedTitle(SoundTrack t) {
        if (t.getCkbContent() != null && !isBlank(t.getCkbContent().getTitle())) return t.getCkbContent().getTitle();
        if (t.getKmrContent() != null && !isBlank(t.getKmrContent().getTitle())) return t.getKmrContent().getTitle();
        return "ناونیشانی نەناسراو";
    }

    private String getCombinedTitle(CreateRequest r) {
        if (r.getCkbContent() != null && !isBlank(r.getCkbContent().getTitle())) return r.getCkbContent().getTitle();
        if (r.getKmrContent() != null && !isBlank(r.getKmrContent().getTitle())) return r.getKmrContent().getTitle();
        return "ناونیشانی نەناسراو";
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String trimOrNull(String s) { if (s == null) return null; String t = s.trim(); return t.isEmpty() ? null : t; }
    private Set<Language> safeLangs(Set<Language> l) { return l == null ? Set.of() : l; }
    private <T> Set<T> safeSet(Set<T> s) { return s == null ? Set.of() : s; }
    private Set<String> cleanStrings(Set<String> input) {
        if (input == null || input.isEmpty()) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : input) if (s != null && !s.isBlank()) out.add(s.trim());
        return out;
    }
}