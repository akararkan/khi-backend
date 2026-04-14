package ak.dev.khi_backend.khi_app.service.publishment.sound;

import ak.dev.khi_backend.khi_app.dto.publishment.sound.SoundTrackDtos.*;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.AttachmentType;
import ak.dev.khi_backend.khi_app.enums.publishment.TrackState;
import ak.dev.khi_backend.khi_app.exceptions.NotFoundException;
import ak.dev.khi_backend.khi_app.exceptions.Errors;
import ak.dev.khi_backend.khi_app.model.publishment.sound.*;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import ak.dev.khi_backend.khi_app.repository.publishment.sound.SoundTrackLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.sound.SoundTrackRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.topic.PublishmentTopicRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SoundTrackService {

    private static final String TOPIC_ENTITY_TYPE = "SOUND";

    private final SoundTrackRepository       soundTrackRepository;
    private final SoundTrackLogRepository    soundTrackLogRepository;
    private final PublishmentTopicRepository topicRepository;
    private final S3Service                  s3Service;

    // =========================================================================
    // دروستکردن (CREATE)
    // =========================================================================

    @CacheEvict(value = "soundTracks", allEntries = true)
    @Transactional
    public Response create(
            CreateRequest        dto,
            MultipartFile        ckbCoverImage,
            MultipartFile        kmrCoverImage,
            MultipartFile        hoverCoverImage,
            List<MultipartFile>  audioFiles,
            List<MultipartFile>  brochureFiles,
            List<MultipartFile>  attachmentFiles
    ) {
        validateCreate(dto);

        try {
            String ckbCoverUrl   = resolveCoverUrl(null, ckbCoverImage);
            String kmrCoverUrl   = resolveCoverUrl(null, kmrCoverImage);
            String hoverCoverUrl = resolveCoverUrl(null, hoverCoverImage);

            PublishmentTopic topic = resolveOrCreateTopic(dto.getTopicId(), dto.getNewTopic());

            SoundTrack entity = SoundTrack.builder()
                    .ckbCoverUrl(ckbCoverUrl)
                    .kmrCoverUrl(kmrCoverUrl)
                    .hoverCoverUrl(hoverCoverUrl)
                    .soundType(dto.getSoundType().trim())
                    .trackState(dto.getTrackState())
                    .albumOfMemories(dto.getAlbumOfMemories() != null && dto.getAlbumOfMemories())
                    .topic(topic)
                    .contentLanguages(new LinkedHashSet<>(safeLangs(dto.getContentLanguages())))
                    .reader(trimOrNull(dto.getReader()))
                    .directors(new LinkedHashSet<>(cleanStrings(dto.getDirectors())))
                    .terms(trimOrNull(dto.getTerms()))
                    .locations(new LinkedHashSet<>(cleanStrings(dto.getLocations())))
                    .thisProjectOfInstitute(dto.isThisProjectOfInstitute())
                    .tagsCkb(new LinkedHashSet<>(cleanStrings(
                            dto.getTags() != null ? dto.getTags().getCkb() : null)))
                    .tagsKmr(new LinkedHashSet<>(cleanStrings(
                            dto.getTags() != null ? dto.getTags().getKmr() : null)))
                    .keywordsCkb(new LinkedHashSet<>(cleanStrings(
                            dto.getKeywords() != null ? dto.getKeywords().getCkb() : null)))
                    .keywordsKmr(new LinkedHashSet<>(cleanStrings(
                            dto.getKeywords() != null ? dto.getKeywords().getKmr() : null)))
                    .albumName(trimOrNull(dto.getAlbumName()))
                    .publishmentYear(dto.getPublishmentYear())
                    .cdNumber(dto.getCdNumber())
                    .totalTracks(dto.getTotalTracks())
                    .build();

            applyContentByLanguages(entity,
                    dto.getContentLanguages(), dto.getCkbContent(), dto.getKmrContent());

            buildAndAttachFiles(entity, dto.getFiles(), audioFiles, brochureFiles);
            buildAndAttachAttachments(entity, dto.getAttachments(), attachmentFiles);

            SoundTrack saved = soundTrackRepository.save(entity);

            createLog(saved.getId(), titleOf(saved), "CREATED",
                    "سەدا دروستکرا — جۆر=" + saved.getSoundType()
                            + " دۆخ=" + saved.getTrackState()
                            + (topic != null ? " بابەتid=" + topic.getId() : ""));

            return toResponse(saved);

        } catch (IOException e) {
            throw Errors.soundStorageFailed("sound.media_upload_failed",
                    Map.of("reason", "کێشە لە ناردنی فایل: " + e.getMessage()), e);
        }
    }

    // =========================================================================
    // نوێکردنەوە (UPDATE)
    // =========================================================================

    @CacheEvict(value = "soundTracks", allEntries = true)
    @Transactional
    public Response update(
            Long                 id,
            UpdateRequest        dto,
            MultipartFile        ckbCoverImage,
            MultipartFile        kmrCoverImage,
            MultipartFile        hoverCoverImage,
            List<MultipartFile>  audioFiles,
            List<MultipartFile>  brochureFiles,
            List<MultipartFile>  attachmentFiles
    ) {
        if (id == null)
            throw Errors.soundValidation("error.validation",
                    Map.of("field", "id", "message", "ئایدی پێویستە"));

        SoundTrack entity = soundTrackRepository.findByIdWithGraph(id)
                .orElseThrow(() -> Errors.soundNotFound(id));

        try {
            // ── Cover images ──────────────────────────────────────────────
            if (hasFile(ckbCoverImage))   entity.setCkbCoverUrl(uploadFile(ckbCoverImage));
            if (hasFile(kmrCoverImage))   entity.setKmrCoverUrl(uploadFile(kmrCoverImage));
            if (hasFile(hoverCoverImage)) entity.setHoverCoverUrl(uploadFile(hoverCoverImage));

            // ── Core ──────────────────────────────────────────────────────
            if (!isBlank(dto.getSoundType()))     entity.setSoundType(dto.getSoundType().trim());
            if (dto.getTrackState()      != null)  entity.setTrackState(dto.getTrackState());
            if (dto.getAlbumOfMemories() != null)  entity.setAlbumOfMemories(dto.getAlbumOfMemories());

            // ── Topic ─────────────────────────────────────────────────────
            if (dto.isClearTopic()) {
                entity.setTopic(null);
            } else if (dto.getTopicId() != null || dto.getNewTopic() != null) {
                entity.setTopic(resolveOrCreateTopic(dto.getTopicId(), dto.getNewTopic()));
            }

            // ── Languages ─────────────────────────────────────────────────
            if (dto.getContentLanguages() != null) {
                entity.getContentLanguages().clear();
                entity.getContentLanguages().addAll(safeLangs(dto.getContentLanguages()));
            }
            applyContentByLanguages(entity,
                    entity.getContentLanguages(), dto.getCkbContent(), dto.getKmrContent());

            // ── Locations ─────────────────────────────────────────────────
            if (dto.getLocations() != null) {
                entity.getLocations().clear();
                entity.getLocations().addAll(cleanStrings(dto.getLocations()));
            }

            // ── Reader (single field) ─────────────────────────────────────
            if (dto.getReader() != null) {
                entity.setReader(trimOrNull(dto.getReader()));
            }

            // ── Directors ─────────────────────────────────────────────────
            if (dto.getDirectors() != null) {
                entity.getDirectors().clear();
                entity.getDirectors().addAll(cleanStrings(dto.getDirectors()));
            }

            // ── Terms ─────────────────────────────────────────────────────
            if (dto.getTerms() != null) entity.setTerms(trimOrNull(dto.getTerms()));

            // ── Institute ─────────────────────────────────────────────────
            if (dto.getThisProjectOfInstitute() != null)
                entity.setThisProjectOfInstitute(dto.getThisProjectOfInstitute());

            // ── Tags & Keywords ───────────────────────────────────────────
            if (dto.getTags() != null) {
                if (dto.getTags().getCkb() != null) {
                    entity.getTagsCkb().clear();
                    entity.getTagsCkb().addAll(cleanStrings(dto.getTags().getCkb()));
                }
                if (dto.getTags().getKmr() != null) {
                    entity.getTagsKmr().clear();
                    entity.getTagsKmr().addAll(cleanStrings(dto.getTags().getKmr()));
                }
            }
            if (dto.getKeywords() != null) {
                if (dto.getKeywords().getCkb() != null) {
                    entity.getKeywordsCkb().clear();
                    entity.getKeywordsCkb().addAll(cleanStrings(dto.getKeywords().getCkb()));
                }
                if (dto.getKeywords().getKmr() != null) {
                    entity.getKeywordsKmr().clear();
                    entity.getKeywordsKmr().addAll(cleanStrings(dto.getKeywords().getKmr()));
                }
            }

            // ── Audio Files ───────────────────────────────────────────────
            // FIX: Guard against null collection before calling .clear().
            // The frontend always sends a files array, so hasFileDtos is always
            // true. If the entity was persisted without any files the collection
            // may be null depending on fetch graph / lazy init state.
            boolean hasFileDtos     = dto.getFiles() != null;
            boolean hasAudioUploads = audioFiles != null
                    && audioFiles.stream().anyMatch(f -> f != null && !f.isEmpty());

            if (hasFileDtos || hasAudioUploads) {
                if (entity.getFiles() != null) {
                    entity.getFiles().clear();
                }
                buildAndAttachFiles(entity, dto.getFiles(), audioFiles, brochureFiles);
            }

            // ── Multi-Album Fields ────────────────────────────────────────
            if (dto.getAlbumName()       != null) entity.setAlbumName(trimOrNull(dto.getAlbumName()));
            if (dto.getPublishmentYear() != null) entity.setPublishmentYear(dto.getPublishmentYear());
            if (dto.getCdNumber()        != null) entity.setCdNumber(dto.getCdNumber());
            if (dto.getTotalTracks()     != null) entity.setTotalTracks(dto.getTotalTracks());

            // ── Attachments (available for SINGLE and MULTI) ─────────────
            // FIX: Same null-safe guard as for files above.
            boolean hasAttachDtos    = dto.getAttachments() != null;
            boolean hasAttachUploads = attachmentFiles != null
                    && attachmentFiles.stream().anyMatch(f -> f != null && !f.isEmpty());

            if (hasAttachDtos || hasAttachUploads) {
                if (entity.getAttachments() != null) {
                    entity.getAttachments().clear();
                }
                buildAndAttachAttachments(entity, dto.getAttachments(), attachmentFiles);
            }

            SoundTrack saved = soundTrackRepository.save(entity);

            createLog(saved.getId(), titleOf(saved), "UPDATED",
                    "سەدا نوێکرایەوە — جۆر=" + saved.getSoundType()
                            + " دۆخ=" + saved.getTrackState());

            return toResponse(saved);

        } catch (IOException e) {
            throw Errors.soundStorageFailed("sound.media_upload_failed",
                    Map.of("reason", "کێشە لە ناردنی فایل: " + e.getMessage()), e);
        }
    }

    // =========================================================================
    // READ
    // =========================================================================

    @Cacheable(value = "soundTracks", key = "'all:p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> getAll(int page, int size) {
        return hydratePage(soundTrackRepository.findAllIds(PageRequest.of(page, size)));
    }

    @Cacheable(value = "soundTracks",
            key = "'state:' + #state.name() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> getByState(TrackState state, int page, int size) {
        if (state == null)
            throw Errors.soundValidation("soundTrack.state.required", Map.of("field", "state"));
        return hydratePage(soundTrackRepository.findIdsByState(state, PageRequest.of(page, size)));
    }

    @Cacheable(value = "soundTracks",
            key = "'soundType:' + #soundType.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> getBySoundType(String soundType, int page, int size) {
        if (isBlank(soundType))
            throw Errors.soundValidation("soundTrack.soundType.required",
                    Map.of("field", "soundType"));
        return hydratePage(soundTrackRepository.findIdsBySoundType(
                soundType.trim(), PageRequest.of(page, size)));
    }

    @Cacheable(value = "soundTracks",
            key = "'topic:' + #topicId + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> getByTopic(Long topicId, int page, int size) {
        if (topicId == null)
            throw Errors.soundValidation("error.validation", Map.of("field", "topicId"));
        return hydratePage(soundTrackRepository.findIdsByTopic(
                topicId, PageRequest.of(page, size)));
    }

    @Cacheable(value = "soundTracks", key = "'album:p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> getAlbumOfMemories(int page, int size) {
        return hydratePage(soundTrackRepository.findIdsAlbumOfMemories(PageRequest.of(page, size)));
    }

    @Cacheable(value = "soundTracks",
            key = "'tag:' + #tag.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> searchByTag(String tag, int page, int size) {
        if (isBlank(tag))
            throw Errors.badRequest("tag.required", Map.of("field", "tag"));
        return hydratePage(soundTrackRepository.findIdsByTag(tag.trim(), PageRequest.of(page, size)));
    }

    @Cacheable(value = "soundTracks",
            key = "'kw:' + #keyword.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> searchByKeyword(String keyword, int page, int size) {
        if (isBlank(keyword))
            throw Errors.badRequest("keyword.required", Map.of("field", "keyword"));
        return hydratePage(soundTrackRepository.findIdsByKeyword(
                keyword.trim(), PageRequest.of(page, size)));
    }

    @Cacheable(value = "soundTracks",
            key = "'search:' + #q.toLowerCase() + ':p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public Page<Response> globalSearch(String q, int page, int size) {
        if (isBlank(q))
            throw Errors.badRequest("keyword.required", Map.of("field", "q"));
        return hydratePage(soundTrackRepository.findIdsByGlobalSearch(
                q.trim(), PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public Response getById(Long id) {
        return toResponse(soundTrackRepository.findByIdWithGraph(id)
                .orElseThrow(() -> Errors.soundNotFound(id)));
    }

    // =========================================================================
    // سڕینەوە (DELETE)
    // =========================================================================

    @CacheEvict(value = "soundTracks", allEntries = true)
    @Transactional
    public void delete(Long id) {
        SoundTrack entity = soundTrackRepository.findByIdWithGraph(id)
                .orElseThrow(() -> Errors.soundNotFound(id));
        createLog(entity.getId(), titleOf(entity), "DELETED",
                "سەدا سڕایەوە — جۆر=" + entity.getSoundType()
                        + " دۆخ=" + entity.getTrackState());
        soundTrackRepository.delete(entity);
    }

    // =========================================================================
    // HYDRATION
    // =========================================================================

    private Page<Response> hydratePage(Page<Long> idPage) {
        if (idPage.isEmpty())
            return new PageImpl<>(Collections.emptyList(),
                    idPage.getPageable(), idPage.getTotalElements());
        return new PageImpl<>(
                hydrateAndSort(idPage.getContent()).stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList()),
                idPage.getPageable(),
                idPage.getTotalElements());
    }

    private List<SoundTrack> hydrateAndSort(List<Long> ids) {
        List<SoundTrack> rows = soundTrackRepository.findAllByIds(ids);
        Map<Long, SoundTrack> indexed = new LinkedHashMap<>(rows.size());
        for (SoundTrack s : rows) indexed.put(s.getId(), s);
        List<SoundTrack> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) { SoundTrack s = indexed.get(id); if (s != null) ordered.add(s); }
        return ordered;
    }

    // =========================================================================
    // FILE BUILDER
    // =========================================================================

    private void buildAndAttachFiles(
            SoundTrack              owner,
            List<FileCreateRequest> fileDtos,
            List<MultipartFile>     audioFiles,
            List<MultipartFile>     brochureFiles
    ) throws IOException {

        int dtoCount   = fileDtos   == null ? 0 : fileDtos.size();
        int audioCount = audioFiles == null ? 0
                : (int) audioFiles.stream().filter(f -> f != null && !f.isEmpty()).count();
        int total      = Math.max(dtoCount, audioCount);
        if (total == 0) return;

        int audioIdx    = 0;
        int brochureIdx = 0;

        for (int i = 0; i < total; i++) {
            FileCreateRequest fDto = (fileDtos != null && i < fileDtos.size())
                    ? fileDtos.get(i) : null;

            MultipartFile audioFile = nextNonEmpty(audioFiles, audioIdx);
            if (audioFile != null) audioIdx = advanceIndex(audioFiles, audioIdx);

            String fileUrl     = null;
            String externalUrl = fDto != null ? trimOrNull(fDto.getExternalUrl()) : null;
            String embedUrl    = fDto != null ? trimOrNull(fDto.getEmbedUrl())    : null;
            long   sizeBytes   = fDto != null ? fDto.getSizeBytes()               : 0L;
            long   durationSec = fDto != null ? fDto.getDurationSeconds()         : 0L;

            if (audioFile != null) {
                fileUrl   = uploadFile(audioFile);
                sizeBytes = audioFile.getSize();
            } else if (fDto != null) {
                fileUrl = trimOrNull(fDto.getFileUrl());
            }

            if (isBlank(fileUrl) && isBlank(externalUrl) && isBlank(embedUrl))
                throw Errors.soundValidation("soundTrack.file.source.required", Map.of(
                        "index", i,
                        "message",
                        "هەر فایلێک پێویستی بە لانیکەم fileUrl، externalUrl، یان embedUrl هەیە"));

            SoundTrackFile file = SoundTrackFile.builder()
                    .fileUrl(fileUrl)
                    .externalUrl(externalUrl)
                    .embedUrl(embedUrl)
                    .title(fDto != null ? trimOrNull(fDto.getTitle())          : null)
                    .fileType(fDto != null ? fDto.getFileType()                : null)
                    .publishmentYear(fDto != null ? fDto.getPublishmentYear()  : null)
                    .sizeBytes(sizeBytes)
                    .durationSeconds(durationSec)
                    .bitRate(fDto != null ? trimOrNull(fDto.getBitRate())      : null)
                    .sampleRate(fDto != null ? trimOrNull(fDto.getSampleRate()): null)
                    .audioChannel(fDto != null ? fDto.getAudioChannel()        : null)
                    .form(fDto != null ? trimOrNull(fDto.getForm())            : null)
                    .genre(fDto != null ? trimOrNull(fDto.getGenre())          : null)
                    .recordingVenue(fDto != null ? trimOrNull(fDto.getRecordingVenue()) : null)
                    .build();

            owner.addFile(file);

            List<BrochureRequest> brochureDtos = (fDto != null && fDto.getBrochures() != null)
                    ? fDto.getBrochures() : Collections.emptyList();
            brochureIdx = buildAndAttachBrochures(file, brochureDtos, brochureFiles, brochureIdx);
        }
    }

    // =========================================================================
    // BROCHURE BUILDER
    // =========================================================================

    private int buildAndAttachBrochures(
            SoundTrackFile        file,
            List<BrochureRequest> brochureDtos,
            List<MultipartFile>   brochureFiles,
            int                   startIndex
    ) throws IOException {

        if (brochureDtos == null || brochureDtos.isEmpty()) return startIndex;

        int idx = startIndex;

        for (int b = 0; b < brochureDtos.size(); b++) {
            BrochureRequest bDto = brochureDtos.get(b);
            if (bDto == null) continue;

            MultipartFile bFile = nextNonEmpty(brochureFiles, idx);
            String imageUrl;

            if (bFile != null) {
                imageUrl = uploadFile(bFile);
                idx      = advanceIndex(brochureFiles, idx);
            } else {
                imageUrl = trimOrNull(bDto.getImageUrl());
            }

            // Skip brochures where no image was resolved (url was blank AND
            // no matching file part was provided).
            if (isBlank(imageUrl)) continue;

            file.addBrochure(SoundTrackBrochure.builder()
                    .imageUrl(imageUrl)
                    .caption(trimOrNull(bDto.getCaption()))
                    .brochureOrder(b)
                    .build());
        }
        return idx;
    }

    // =========================================================================
    // ATTACHMENT BUILDER  (available for SINGLE and MULTI)
    // =========================================================================

    private void buildAndAttachAttachments(
            SoundTrack              owner,
            List<AttachmentRequest> dtos,
            List<MultipartFile>     attachmentFiles
    ) throws IOException {

        boolean hasDtos    = dtos != null && !dtos.isEmpty();
        boolean hasUploads = attachmentFiles != null
                && attachmentFiles.stream().anyMatch(f -> f != null && !f.isEmpty());

        // Attachments are fully optional — nothing to do if neither provided
        if (!hasDtos && !hasUploads) return;

        int dtoCount  = dtos == null ? 0 : dtos.size();
        int fileCount = attachmentFiles == null ? 0
                : (int) attachmentFiles.stream().filter(f -> f != null && !f.isEmpty()).count();
        int total     = Math.max(dtoCount, fileCount);

        int attachIdx = 0;

        for (int i = 0; i < total; i++) {
            AttachmentRequest aDto = (dtos != null && i < dtos.size()) ? dtos.get(i) : null;

            MultipartFile aFile = nextNonEmpty(attachmentFiles, attachIdx);
            if (aFile != null) attachIdx = advanceIndex(attachmentFiles, attachIdx);

            String fileUrl  = null;
            long   fileSize = aDto != null ? aDto.getSizeBytes() : 0L;

            if (aFile != null) {
                fileUrl  = uploadFile(aFile);
                fileSize = aFile.getSize();
            } else if (aDto != null) {
                fileUrl = trimOrNull(aDto.getFileUrl());
            }

            // Skip entries with no URL — graceful handling
            if (isBlank(fileUrl)) continue;

            // FIX: Default attachmentType to OTHER when the DTO field is null.
            // Previously @NotNull on AttachmentRequest caused a
            // ConstraintViolationException → 500 when the frontend omitted it.
            AttachmentType resolvedType = (aDto != null && aDto.getAttachmentType() != null)
                    ? aDto.getAttachmentType()
                    : AttachmentType.OTHER;

            owner.addAttachment(SoundTrackAttachment.builder()
                    .fileUrl(fileUrl)
                    .title(aDto != null ? trimOrNull(aDto.getTitle()) : null)
                    .attachmentType(resolvedType)
                    .sizeBytes(fileSize)
                    .mimeType(aDto != null ? trimOrNull(aDto.getMimeType()) : null)
                    .attachmentOrder(i)
                    .build());
        }
    }

    // =========================================================================
    // TOPIC HELPERS
    // =========================================================================

    private PublishmentTopic resolveOrCreateTopic(Long topicId, InlineTopicRequest newTopic) {
        if (topicId != null) return findSoundTopicOrThrow(topicId);
        if (newTopic != null) {
            if (isBlank(newTopic.getNameCkb()) && isBlank(newTopic.getNameKmr()))
                throw Errors.soundValidation("error.validation", Map.of(
                        "message",
                        "بابەتی نوێ پێویستی بە لانیکەم ناوێکی کوردییە (ناوەندی یان باکوور)"));
            PublishmentTopic created = topicRepository.save(
                    PublishmentTopic.builder()
                            .entityType(TOPIC_ENTITY_TYPE)
                            .nameCkb(trimOrNull(newTopic.getNameCkb()))
                            .nameKmr(trimOrNull(newTopic.getNameKmr()))
                            .build());
            log.info("بابەتی SOUND دروستکرا inline id={}", created.getId());
            return created;
        }
        return null;
    }

    private PublishmentTopic findSoundTopicOrThrow(Long topicId) {
        PublishmentTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> Errors.notFound("topic.not_found", Map.of("id", topicId)));
        if (!TOPIC_ENTITY_TYPE.equals(topic.getEntityType()))
            throw Errors.soundValidation("topic.type.mismatch", Map.of(
                    "message", "بابەت id=" + topicId + " بۆ '"
                            + topic.getEntityType() + "'ە، چاوەڕوان دەکرێت 'SOUND' بێت"));
        return topic;
    }

    // =========================================================================
    // CONTENT HELPERS
    // =========================================================================

    private void applyContentByLanguages(
            SoundTrack entity, Set<Language> langs,
            LanguageContentDto ckb, LanguageContentDto kmr
    ) {
        Set<Language> safe = safeLangs(langs);
        entity.setCkbContent(safe.contains(Language.CKB) ? buildContent(ckb) : null);
        entity.setKmrContent(safe.contains(Language.KMR) ? buildContent(kmr) : null);
    }

    private SoundTrackContent buildContent(LanguageContentDto dto) {
        if (dto == null) return null;
        if (isBlank(dto.getTitle()) && isBlank(dto.getDescription())
               ) return null;
        return SoundTrackContent.builder()
                .title(trimOrNull(dto.getTitle()))
                .description(trimOrNull(dto.getDescription()))
                .build();
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    private void validateCreate(CreateRequest dto) {
        if (dto == null)
            throw Errors.soundValidation("error.validation", Map.of("field", "data"));
        if (isBlank(dto.getSoundType()))
            throw Errors.soundValidation("error.validation",
                    Map.of("field", "soundType", "message", "جۆری دەنگ پێویستە"));
        if (dto.getTrackState() == null)
            throw Errors.soundValidation("error.validation",
                    Map.of("field", "trackState", "message", "دۆخی تراک پێویستە"));
        if (safeLangs(dto.getContentLanguages()).isEmpty())
            throw Errors.soundValidation("soundTrack.languages.required",
                    Map.of("field", "contentLanguages"));
    }

    // =========================================================================
    // ENTITY → DTO
    // =========================================================================

    private Response toResponse(SoundTrack s) {
        List<FileResponse> fileResponses = s.getFiles() == null ? List.of()
                : new ArrayList<>(s.getFiles()).stream()
                .map(this::toFileResponse).collect(Collectors.toList());

        long totalDuration = fileResponses.stream().mapToLong(FileResponse::getDurationSeconds).sum();
        long totalSize     = fileResponses.stream().mapToLong(FileResponse::getSizeBytes).sum();

        List<AttachmentResponse> attachResponses = s.getAttachments() == null ? List.of()
                : new ArrayList<>(s.getAttachments()).stream()
                .map(this::toAttachmentResponse).collect(Collectors.toList());

        return Response.builder()
                .id(s.getId())
                .ckbCoverUrl(s.getCkbCoverUrl()).kmrCoverUrl(s.getKmrCoverUrl())
                .hoverCoverUrl(s.getHoverCoverUrl())
                .soundType(s.getSoundType()).trackState(s.getTrackState())
                .albumOfMemories(s.isAlbumOfMemories())
                .topicId(s.getTopic()      != null ? s.getTopic().getId()      : null)
                .topicNameCkb(s.getTopic() != null ? s.getTopic().getNameCkb() : null)
                .topicNameKmr(s.getTopic() != null ? s.getTopic().getNameKmr() : null)
                .contentLanguages(s.getContentLanguages() != null
                        ? new LinkedHashSet<>(s.getContentLanguages()) : new LinkedHashSet<>())
                .ckbContent(toContentDto(s.getCkbContent()))
                .kmrContent(toContentDto(s.getKmrContent()))
                .locations(new LinkedHashSet<>(safeSet(s.getLocations())))
                .reader(s.getReader())
                .directors(new LinkedHashSet<>(safeSet(s.getDirectors())))
                .terms(s.getTerms())
                .thisProjectOfInstitute(s.isThisProjectOfInstitute())
                .tags(BilingualSet.builder()
                        .ckb(new LinkedHashSet<>(safeSet(s.getTagsCkb())))
                        .kmr(new LinkedHashSet<>(safeSet(s.getTagsKmr()))).build())
                .keywords(BilingualSet.builder()
                        .ckb(new LinkedHashSet<>(safeSet(s.getKeywordsCkb())))
                        .kmr(new LinkedHashSet<>(safeSet(s.getKeywordsKmr()))).build())
                .files(fileResponses)
                .totalDurationSeconds(totalDuration).totalSizeBytes(totalSize)
                .albumName(s.getAlbumName()).publishmentYear(s.getPublishmentYear())
                .cdNumber(s.getCdNumber()).totalTracks(s.getTotalTracks())
                .attachments(attachResponses)
                .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                .build();
    }

    private FileResponse toFileResponse(SoundTrackFile f) {
        List<BrochureResponse> brochures = f.getBrochures() == null ? List.of()
                : new ArrayList<>(f.getBrochures()).stream()
                .map(b -> BrochureResponse.builder()
                        .id(b.getId()).imageUrl(b.getImageUrl())
                        .caption(b.getCaption()).brochureOrder(b.getBrochureOrder()).build())
                .collect(Collectors.toList());

        return FileResponse.builder()
                .id(f.getId()).fileUrl(f.getFileUrl())
                .externalUrl(f.getExternalUrl()).embedUrl(f.getEmbedUrl())
                .title(f.getTitle()).fileType(f.getFileType())
                .publishmentYear(f.getPublishmentYear())
                .sizeBytes(f.getSizeBytes()).durationSeconds(f.getDurationSeconds())
                .durationMinutes(f.getDurationMinutes())
                .bitRate(f.getBitRate()).sampleRate(f.getSampleRate())
                .audioChannel(f.getAudioChannel()).form(f.getForm())
                .genre(f.getGenre()).recordingVenue(f.getRecordingVenue())
                .brochures(brochures)
                .build();
    }

    private AttachmentResponse toAttachmentResponse(SoundTrackAttachment a) {
        return AttachmentResponse.builder()
                .id(a.getId()).fileUrl(a.getFileUrl()).title(a.getTitle())
                .attachmentType(a.getAttachmentType()).sizeBytes(a.getSizeBytes())
                .mimeType(a.getMimeType()).attachmentOrder(a.getAttachmentOrder())
                .build();
    }

    private LanguageContentDto toContentDto(SoundTrackContent c) {
        if (c == null) return null;
        return LanguageContentDto.builder()
                .title(c.getTitle()).description(c.getDescription()).build();
    }

    // =========================================================================
    // LOGGING
    // =========================================================================

    private void createLog(Long id, String title, String action, String details) {
        try {
            soundTrackLogRepository.save(SoundTrackLog.builder()
                    .soundTrackRefId(id).soundTrackTitle(title)
                    .action(action).details(details).actorName("system").build());
        } catch (Exception e) {
            log.warn("شکستی تۆمارکردنی لۆگی سەدا | id={}", id, e);
        }
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private String titleOf(SoundTrack s) {
        if (s == null) return "";
        if (s.getCkbContent() != null && !isBlank(s.getCkbContent().getTitle()))
            return s.getCkbContent().getTitle();
        if (s.getKmrContent() != null && !isBlank(s.getKmrContent().getTitle()))
            return s.getKmrContent().getTitle();
        return "سەدا#" + s.getId();
    }

    private boolean hasFile(MultipartFile f)   { return f != null && !f.isEmpty(); }

    private String uploadFile(MultipartFile f) throws IOException {
        return s3Service.upload(f.getBytes(), f.getOriginalFilename(), f.getContentType());
    }

    private String resolveCoverUrl(String dtoUrl, MultipartFile file) throws IOException {
        if (hasFile(file)) return uploadFile(file);
        return isBlank(dtoUrl) ? null : dtoUrl.trim();
    }

    private boolean isBlank(String s)   { return s == null || s.isBlank(); }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Set<Language> safeLangs(Set<Language> in) {
        return in == null ? new LinkedHashSet<>() : new LinkedHashSet<>(in);
    }

    private Set<String> safeSet(Set<String> in) {
        return in == null ? new LinkedHashSet<>() : new LinkedHashSet<>(in);
    }

    private Set<String> cleanStrings(Set<String> in) {
        if (in == null) return new LinkedHashSet<>();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : in) { String t = trimOrNull(s); if (t != null) out.add(t); }
        return out;
    }

    private MultipartFile nextNonEmpty(List<MultipartFile> files, int start) {
        if (files == null) return null;
        for (int i = start; i < files.size(); i++)
            if (files.get(i) != null && !files.get(i).isEmpty()) return files.get(i);
        return null;
    }

    private int advanceIndex(List<MultipartFile> files, int start) {
        if (files == null) return start;
        for (int i = start; i < files.size(); i++)
            if (files.get(i) != null && !files.get(i).isEmpty()) return i + 1;
        return files.size();
    }
}