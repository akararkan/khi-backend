package ak.dev.khi_backend.khi_app.service.publishment.sound;

import ak.dev.khi_backend.khi_app.dto.publishment.sound.SoundTrackDtos.*;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.publishment.FileType;
import ak.dev.khi_backend.khi_app.enums.publishment.TrackState;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrack;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrackContent;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrackFile;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrackLog;
import ak.dev.khi_backend.khi_app.repository.publishment.sound.SoundTrackLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.sound.SoundTrackRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class SoundTrackService {

    private final SoundTrackRepository soundTrackRepository;
    private final SoundTrackLogRepository soundTrackLogRepository;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;

    // ============================================================
    // CREATE
    // ============================================================
    @Transactional
    public Response addSoundTrack(CreateRequest request, List<MultipartFile> audioFiles, MultipartFile coverImage) {

        int uploadedCount = (audioFiles == null) ? 0 : (int) audioFiles.stream().filter(f -> f != null && !f.isEmpty()).count();
        int linksCount = (request.getFiles() == null) ? 0 : (int) request.getFiles().stream().filter(Objects::nonNull).count();

        log.info("Adding SoundTrack: {} | uploadedFiles={} | linkFiles={}",
                getCombinedTitle(request), uploadedCount, linksCount);

        validate(request, true);

        // SINGLE => total (uploads + links) must be <= 1
        if (request.getTrackState() == TrackState.SINGLE && (uploadedCount + linksCount) > 1) {
            throw new IllegalArgumentException("SINGLE track state can only have 1 audio source (upload or link)");
        }

        // Cover upload (optional)
        String coverUrl = null;
        if (coverImage != null && !coverImage.isEmpty()) {
            try {
                coverUrl = s3Service.upload(
                        coverImage.getBytes(),
                        coverImage.getOriginalFilename(),
                        coverImage.getContentType()
                );
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload cover image", e);
            }
        }

        SoundTrack soundTrack = SoundTrack.builder()
                .coverUrl(coverUrl)
                .soundType(request.getSoundType())
                .trackState(request.getTrackState())
                .contentLanguages(new LinkedHashSet<>(safeLangs(request.getContentLanguages())))
                .locations(new LinkedHashSet<>(safeSet(request.getLocations())))
                .director(request.getDirector())
                .isThisProjectOfInstitute(request.isThisProjectOfInstitute())
                .tagsCkb(new LinkedHashSet<>(safeSet(request.getTags() != null ? request.getTags().getCkb() : null)))
                .tagsKmr(new LinkedHashSet<>(safeSet(request.getTags() != null ? request.getTags().getKmr() : null)))
                .keywordsCkb(new LinkedHashSet<>(safeSet(request.getKeywords() != null ? request.getKeywords().getCkb() : null)))
                .keywordsKmr(new LinkedHashSet<>(safeSet(request.getKeywords() != null ? request.getKeywords().getKmr() : null)))
                .files(new ArrayList<>())
                .build();

        applyContentByLanguages(soundTrack, request);

        // 1) Upload audio files (optional)
        if (audioFiles != null && !audioFiles.isEmpty()) {
            int i = 0;
            for (MultipartFile audioFile : audioFiles) {
                if (audioFile == null || audioFile.isEmpty()) continue;

                String readerName = request.getReaderNames() != null && i < request.getReaderNames().size()
                        ? request.getReaderNames().get(i)
                        : null;

                try {
                    String audioUrl = s3Service.upload(
                            audioFile.getBytes(),
                            audioFile.getOriginalFilename(),
                            audioFile.getContentType()
                    );

                    FileType fileType = determineFileType(audioFile);

                    SoundTrackFile trackFile = SoundTrackFile.builder()
                            .fileUrl(audioUrl)
                            .externalUrl(null)
                            .embedUrl(null)
                            .fileType(fileType)
                            .durationSeconds(0)
                            .sizeBytes(audioFile.getSize())
                            .readerName(readerName)
                            .build();

                    soundTrack.addFile(trackFile);
                    i++;

                } catch (IOException e) {
                    throw new RuntimeException("Failed to upload audio file: " + audioFile.getOriginalFilename(), e);
                }
            }
        }

        // 2) Link files from DTO (optional) ✅
        attachFilesFromDto(soundTrack, request.getFiles());

        SoundTrack savedTrack = soundTrackRepository.save(soundTrack);

        logAction(savedTrack, "CREATED",
                String.format("SoundTrack '%s' created with %d files", getCombinedTitle(savedTrack), savedTrack.getFiles().size()));

        return mapToResponse(savedTrack);
    }

    // ============================================================
    // UPDATE
    // ============================================================
    @Transactional
    public Response updateSoundTrack(Long id, UpdateRequest request, List<MultipartFile> audioFiles, MultipartFile coverImage) {
        log.info("Updating SoundTrack with ID: {}", id);

        SoundTrack soundTrack = soundTrackRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SoundTrack not found with id: " + id));

        validate(request);

        Map<String, Object> changes = new HashMap<>();

        // Cover update (optional)
        if (coverImage != null && !coverImage.isEmpty()) {
            try {
                String newCoverUrl = s3Service.upload(
                        coverImage.getBytes(),
                        coverImage.getOriginalFilename(),
                        coverImage.getContentType()
                );
                soundTrack.setCoverUrl(newCoverUrl);
                changes.put("coverUrl", "Updated");
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload cover image", e);
            }
        }

        if (request.getSoundType() != null && request.getSoundType() != soundTrack.getSoundType()) {
            changes.put("soundType", Map.of("old", soundTrack.getSoundType(), "new", request.getSoundType()));
            soundTrack.setSoundType(request.getSoundType());
        }

        if (request.getLocations() != null) {
            soundTrack.setLocations(new LinkedHashSet<>(request.getLocations()));
        }

        if (request.getDirector() != null) {
            soundTrack.setDirector(request.getDirector());
        }

        if (request.getIsThisProjectOfInstitute() != null) {
            soundTrack.setThisProjectOfInstitute(request.getIsThisProjectOfInstitute());
        }

        if (request.getTrackState() != null && request.getTrackState() != soundTrack.getTrackState()) {
            changes.put("trackState", Map.of("old", soundTrack.getTrackState(), "new", request.getTrackState()));
            soundTrack.setTrackState(request.getTrackState());
        }

        // languages
        if (request.getContentLanguages() != null) {
            soundTrack.setContentLanguages(new LinkedHashSet<>(request.getContentLanguages()));
        }
        applyContentByLanguages(soundTrack, request);

        // tags/keywords
        replaceBilingualSets(soundTrack, request);

        boolean hasUploads = audioFiles != null && audioFiles.stream().anyMatch(f -> f != null && !f.isEmpty());
        boolean hasLinks = request.getFiles() != null; // important: if provided, user wants to update link list

        // If uploads OR links provided => replace files with new combined list (best behavior)
        if (hasUploads || hasLinks) {

            int uploadedCount = hasUploads ? (int) audioFiles.stream().filter(f -> f != null && !f.isEmpty()).count() : 0;
            int linksCount = request.getFiles() != null ? (int) request.getFiles().stream().filter(Objects::nonNull).count() : 0;

            if (soundTrack.getTrackState() == TrackState.SINGLE && (uploadedCount + linksCount) > 1) {
                throw new IllegalArgumentException("SINGLE track state can only have 1 audio source (upload or link)");
            }

            soundTrack.getFiles().clear();

            // uploads first
            if (hasUploads) {
                int i = 0;
                for (MultipartFile audioFile : audioFiles) {
                    if (audioFile == null || audioFile.isEmpty()) continue;

                    String readerName = request.getReaderNames() != null && i < request.getReaderNames().size()
                            ? request.getReaderNames().get(i)
                            : null;

                    try {
                        String audioUrl = s3Service.upload(
                                audioFile.getBytes(),
                                audioFile.getOriginalFilename(),
                                audioFile.getContentType()
                        );

                        FileType fileType = determineFileType(audioFile);

                        SoundTrackFile trackFile = SoundTrackFile.builder()
                                .fileUrl(audioUrl)
                                .externalUrl(null)
                                .embedUrl(null)
                                .fileType(fileType)
                                .durationSeconds(0)
                                .sizeBytes(audioFile.getSize())
                                .readerName(readerName)
                                .build();

                        soundTrack.addFile(trackFile);
                        i++;

                    } catch (IOException e) {
                        throw new RuntimeException("Failed to upload audio file: " + audioFile.getOriginalFilename(), e);
                    }
                }

                changes.put("files", "Replaced with uploaded files: " + uploadedCount);
            }

            // links (optional) ✅
            attachFilesFromDto(soundTrack, request.getFiles());
            if (hasLinks) {
                changes.put("links", "Updated link files");
            }
        }

        SoundTrack updatedTrack = soundTrackRepository.save(soundTrack);

        if (!changes.isEmpty()) {
            try {
                String changesJson = objectMapper.writeValueAsString(changes);
                logAction(updatedTrack, "UPDATED", changesJson);
            } catch (Exception e) {
                log.error("Failed to serialize changes", e);
                logAction(updatedTrack, "UPDATED", "SoundTrack updated");
            }
        }

        return mapToResponse(updatedTrack);
    }

    // ============================================================
    // DTO LINKS → ENTITY FILES (NEW ✅)
    // ============================================================
    private void attachFilesFromDto(SoundTrack soundTrack, List<FileCreateRequest> files) {
        if (files == null || files.isEmpty()) return;

        // prevent duplicates by best link (fileUrl OR embedUrl OR externalUrl) + fileType
        Set<String> existing = new HashSet<>();
        if (soundTrack.getFiles() != null) {
            for (SoundTrackFile f : soundTrack.getFiles()) {
                String key = fileKey(f.getFileType(), f.getFileUrl(), f.getEmbedUrl(), f.getExternalUrl());
                if (key != null) existing.add(key);
            }
        }

        for (FileCreateRequest f : files) {
            if (f == null) continue;

            String fileUrl = trimOrNull(f.getFileUrl());
            String externalUrl = trimOrNull(f.getExternalUrl());
            String embedUrl = trimOrNull(f.getEmbedUrl());

            boolean hasAny = !isBlank(fileUrl) || !isBlank(externalUrl) || !isBlank(embedUrl);
            if (!hasAny) {
                throw new IllegalArgumentException("Each file must have fileUrl OR externalUrl OR embedUrl");
            }

            FileType fileType = f.getFileType() != null ? f.getFileType() : FileType.OTHER;

            String key = fileKey(fileType, fileUrl, embedUrl, externalUrl);
            if (key == null || existing.contains(key)) continue;

            long duration = f.getDurationSeconds() != null ? Math.max(0, f.getDurationSeconds()) : 0;
            long size = f.getSizeBytes() != null ? Math.max(0, f.getSizeBytes()) : 0;

            SoundTrackFile entity = SoundTrackFile.builder()
                    .fileUrl(fileUrl)
                    .externalUrl(externalUrl)
                    .embedUrl(embedUrl)
                    .fileType(fileType)
                    .durationSeconds(duration)
                    .sizeBytes(size)
                    .readerName(trimOrNull(f.getReaderName()))
                    .build();

            soundTrack.addFile(entity);
            existing.add(key);
        }
    }

    private String fileKey(FileType type, String fileUrl, String embedUrl, String externalUrl) {
        String best = trimOrNull(fileUrl);
        if (best == null) best = trimOrNull(embedUrl);
        if (best == null) best = trimOrNull(externalUrl);
        if (best == null) return null;
        return (type != null ? type.name() : "OTHER") + "|" + best;
    }

    // ============================================================
    // GET ALL
    // ============================================================
    @Transactional(readOnly = true)
    public List<Response> getAllSoundTracks() {
        return soundTrackRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ============================================================
    // DELETE
    // ============================================================
    @Transactional
    public void deleteSoundTrack(Long id) {
        SoundTrack soundTrack = soundTrackRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SoundTrack not found with id: " + id));

        String trackTitle = getCombinedTitle(soundTrack);
        logAction(soundTrack, "DELETED", String.format("SoundTrack '%s' deleted", trackTitle));
        soundTrackRepository.delete(soundTrack);
    }

    // ============================================================
    // SEARCH BY TAG / KEYWORD / LOCATION (unchanged)
    // ============================================================
    @Transactional(readOnly = true)
    public List<Response> searchByTag(String tag, String language) {
        if (isBlank(tag)) return List.of();
        String t = tag.trim();

        List<SoundTrack> results;
        if ("ckb".equalsIgnoreCase(language)) {
            results = soundTrackRepository.findAll().stream()
                    .filter(track -> track.getTagsCkb().stream().anyMatch(x -> x.equalsIgnoreCase(t)))
                    .collect(Collectors.toList());
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = soundTrackRepository.findAll().stream()
                    .filter(track -> track.getTagsKmr().stream().anyMatch(x -> x.equalsIgnoreCase(t)))
                    .collect(Collectors.toList());
        } else {
            Set<SoundTrack> set = new LinkedHashSet<>();
            set.addAll(soundTrackRepository.findAll().stream()
                    .filter(track -> track.getTagsCkb().stream().anyMatch(x -> x.equalsIgnoreCase(t)))
                    .collect(Collectors.toList()));
            set.addAll(soundTrackRepository.findAll().stream()
                    .filter(track -> track.getTagsKmr().stream().anyMatch(x -> x.equalsIgnoreCase(t)))
                    .collect(Collectors.toList()));
            results = new ArrayList<>(set);
        }

        return results.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Response> searchByKeyword(String keyword, String language) {
        if (isBlank(keyword)) return List.of();
        String kw = keyword.trim();

        List<SoundTrack> results;
        if ("ckb".equalsIgnoreCase(language)) {
            results = soundTrackRepository.findAll().stream()
                    .filter(track -> track.getKeywordsCkb().stream().anyMatch(k -> k.equalsIgnoreCase(kw)))
                    .collect(Collectors.toList());
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = soundTrackRepository.findAll().stream()
                    .filter(track -> track.getKeywordsKmr().stream().anyMatch(k -> k.equalsIgnoreCase(kw)))
                    .collect(Collectors.toList());
        } else {
            Set<SoundTrack> set = new LinkedHashSet<>();
            set.addAll(soundTrackRepository.findAll().stream()
                    .filter(track -> track.getKeywordsCkb().stream().anyMatch(k -> k.equalsIgnoreCase(kw)))
                    .collect(Collectors.toList()));
            set.addAll(soundTrackRepository.findAll().stream()
                    .filter(track -> track.getKeywordsKmr().stream().anyMatch(k -> k.equalsIgnoreCase(kw)))
                    .collect(Collectors.toList()));
            results = new ArrayList<>(set);
        }

        return results.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Response> searchByLocation(String location) {
        if (isBlank(location)) return List.of();
        return soundTrackRepository.findAll().stream()
                .filter(track -> track.getLocations().stream().anyMatch(loc -> loc.equalsIgnoreCase(location.trim())))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ============================================================
    // VALIDATION + LANGUAGE LOGIC (unchanged)
    // ============================================================
    private void validate(CreateRequest request, boolean isCreate) {
        if (request == null) throw new IllegalArgumentException("Request cannot be null");

        Set<Language> langs = safeLangs(request.getContentLanguages());
        if (langs.isEmpty()) throw new IllegalArgumentException("At least one content language is required");

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

    private void applyContentByLanguages(SoundTrack soundTrack, UpdateRequest request) {
        Set<Language> langs = safeLangs(soundTrack.getContentLanguages());

        if (langs.contains(Language.CKB)) {
            if (request.getCkbContent() != null) {
                soundTrack.setCkbContent(buildContent(request.getCkbContent()));
            }
        } else {
            soundTrack.setCkbContent(null);
            soundTrack.getTagsCkb().clear();
            soundTrack.getKeywordsCkb().clear();
        }

        if (langs.contains(Language.KMR)) {
            if (request.getKmrContent() != null) {
                soundTrack.setKmrContent(buildContent(request.getKmrContent()));
            }
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

    private FileType determineFileType(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        if (contentType != null) {
            if (contentType.contains("mp3") || (filename != null && filename.endsWith(".mp3"))) return FileType.MP3;
            if (contentType.contains("wav") || (filename != null && filename.endsWith(".wav"))) return FileType.WAV;
            if (contentType.contains("ogg") || (filename != null && filename.endsWith(".ogg"))) return FileType.OGG;
            if (contentType.contains("aac") || (filename != null && filename.endsWith(".aac"))) return FileType.AAC;
            if (contentType.contains("flac") || (filename != null && filename.endsWith(".flac"))) return FileType.FLAC;
        }
        return FileType.OTHER;
    }

    // ============================================================
    // RESPONSE MAPPER (UPDATED ✅ includes externalUrl/embedUrl)
    // ============================================================
    private Response mapToResponse(SoundTrack soundTrack) {
        List<FileResponse> fileDTOs = soundTrack.getFiles().stream()
                .map(file -> FileResponse.builder()
                        .id(file.getId())
                        .fileUrl(file.getFileUrl())
                        .externalUrl(file.getExternalUrl())
                        .embedUrl(file.getEmbedUrl())
                        .fileType(file.getFileType())
                        .durationSeconds(file.getDurationSeconds())
                        .sizeBytes(file.getSizeBytes())
                        .readerName(file.getReaderName())
                        .build())
                .collect(Collectors.toList());

        long totalDuration = soundTrack.getFiles().stream().mapToLong(SoundTrackFile::getDurationSeconds).sum();
        long totalSize = soundTrack.getFiles().stream().mapToLong(SoundTrackFile::getSizeBytes).sum();

        Response response = Response.builder()
                .id(soundTrack.getId())
                .coverUrl(soundTrack.getCoverUrl())
                .soundType(soundTrack.getSoundType())
                .trackState(soundTrack.getTrackState())
                .contentLanguages(soundTrack.getContentLanguages() != null ?
                        new LinkedHashSet<>(soundTrack.getContentLanguages()) : new LinkedHashSet<>())
                .locations(soundTrack.getLocations())
                .director(soundTrack.getDirector())
                .isThisProjectOfInstitute(soundTrack.isThisProjectOfInstitute())
                .files(fileDTOs)
                .totalDurationSeconds(totalDuration)
                .totalSizeBytes(totalSize)
                .createdAt(soundTrack.getCreatedAt())
                .updatedAt(soundTrack.getUpdatedAt())
                .build();

        if (soundTrack.getCkbContent() != null) {
            response.setCkbContent(LanguageContentDto.builder()
                    .title(soundTrack.getCkbContent().getTitle())
                    .description(soundTrack.getCkbContent().getDescription())
                    .reading(soundTrack.getCkbContent().getReading())
                    .build());
        }

        if (soundTrack.getKmrContent() != null) {
            response.setKmrContent(LanguageContentDto.builder()
                    .title(soundTrack.getKmrContent().getTitle())
                    .description(soundTrack.getKmrContent().getDescription())
                    .reading(soundTrack.getKmrContent().getReading())
                    .build());
        }

        response.setTags(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(soundTrack.getTagsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(soundTrack.getTagsKmr())))
                .build());

        response.setKeywords(BilingualSet.builder()
                .ckb(new LinkedHashSet<>(safeSet(soundTrack.getKeywordsCkb())))
                .kmr(new LinkedHashSet<>(safeSet(soundTrack.getKeywordsKmr())))
                .build());

        return response;
    }

    private void logAction(SoundTrack soundTrack, String action, String details) {
        SoundTrackLog logEntry = SoundTrackLog.builder()
                .soundTrack(soundTrack)
                .action(action)
                .actorId("system")
                .actorName("System")
                .details(details)
                .createdAt(LocalDateTime.now())
                .build();

        soundTrackLogRepository.save(logEntry);
    }

    private String getCombinedTitle(SoundTrack track) {
        if (track.getCkbContent() != null && !isBlank(track.getCkbContent().getTitle())) return track.getCkbContent().getTitle();
        if (track.getKmrContent() != null && !isBlank(track.getKmrContent().getTitle())) return track.getKmrContent().getTitle();
        return "Unknown";
    }

    private String getCombinedTitle(CreateRequest request) {
        if (request.getCkbContent() != null && !isBlank(request.getCkbContent().getTitle())) return request.getCkbContent().getTitle();
        if (request.getKmrContent() != null && !isBlank(request.getKmrContent().getTitle())) return request.getKmrContent().getTitle();
        return "Unknown";
    }

    // utils
    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String trimOrNull(String s) { if (s == null) return null; String t = s.trim(); return t.isEmpty() ? null : t; }
    private Set<Language> safeLangs(Set<Language> langs) { return langs == null ? Set.of() : langs; }
    private <T> Set<T> safeSet(Set<T> s) { return s == null ? Set.of() : s; }

    private Set<String> cleanStrings(Set<String> input) {
        if (input == null || input.isEmpty()) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : input) if (s != null && !s.isBlank()) out.add(s.trim());
        return out;
    }
}
