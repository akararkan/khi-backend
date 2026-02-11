package ak.dev.khi_backend.khi_app.service.publishment.sound;

import ak.dev.khi_backend.khi_app.dto.publishment.sound.SoundTrackDtos.*;
import ak.dev.khi_backend.khi_app.enums.publishment.FileType;
import ak.dev.khi_backend.khi_app.enums.publishment.TrackState;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrack;
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

    /**
     * ADD - Create a new SoundTrack with files (SINGLE or MULTI)
     * Uploads audio files to S3 and saves URLs
     */
    @Transactional
    public Response addSoundTrack(CreateRequest request, List<MultipartFile> audioFiles,
                                  MultipartFile coverImage) {
        log.info("Adding SoundTrack: {} with {} audio files", request.getTitle(), audioFiles.size());

        // Validate SINGLE state should have only 1 file
        if (request.getTrackState() == TrackState.SINGLE && audioFiles.size() > 1) {
            throw new IllegalArgumentException("SINGLE track state can only have 1 audio file");
        }

        // Upload cover image to S3 if provided
        String coverUrl = null;
        if (coverImage != null && !coverImage.isEmpty()) {
            try {
                coverUrl = s3Service.upload(
                        coverImage.getBytes(),
                        coverImage.getOriginalFilename(),
                        coverImage.getContentType()
                );
                log.info("Cover image uploaded to S3: {}", coverUrl);
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload cover image", e);
            }
        }

        // Build SoundTrack entity
        SoundTrack soundTrack = SoundTrack.builder()
                .title(request.getTitle())
                .coverUrl(coverUrl)
                .description(request.getDescription())
                .reading(request.getReading())
                .soundType(request.getSoundType())
                .language(request.getLanguage())
                .locations(Optional.ofNullable(request.getLocations()).orElse(new HashSet<>())) // CHANGED
                .director(request.getDirector())
                .isThisProjectOfInstitute(request.isThisProjectOfInstitute())
                .trackState(request.getTrackState())
                .keywords(Optional.ofNullable(request.getKeywords()).orElse(new HashSet<>()))
                .tags(Optional.ofNullable(request.getTags()).orElse(new HashSet<>()))
                .files(new ArrayList<>())
                .build();

        // Upload audio files to S3 and add to soundtrack
        for (int i = 0; i < audioFiles.size(); i++) {
            MultipartFile audioFile = audioFiles.get(i);
            String readerName = request.getReaderNames() != null && i < request.getReaderNames().size()
                    ? request.getReaderNames().get(i)
                    : null;

            try {
                // Upload to S3
                String audioUrl = s3Service.upload(
                        audioFile.getBytes(),
                        audioFile.getOriginalFilename(),
                        audioFile.getContentType()
                );

                // Determine file type from content type or extension
                FileType fileType = determineFileType(audioFile);

                // Create file entity
                SoundTrackFile trackFile = SoundTrackFile.builder()
                        .fileUrl(audioUrl)
                        .fileType(fileType)
                        .durationSeconds(0) // Can be calculated if needed
                        .sizeBytes(audioFile.getSize())
                        .readerName(readerName)
                        .build();

                soundTrack.addFile(trackFile);
                log.info("Audio file {} uploaded to S3: {}", i + 1, audioUrl);

            } catch (IOException e) {
                throw new RuntimeException("Failed to upload audio file: " + audioFile.getOriginalFilename(), e);
            }
        }

        // Save
        SoundTrack savedTrack = soundTrackRepository.save(soundTrack);

        // Log to SoundTrackLog table
        logAction(savedTrack, "CREATED",
                String.format("SoundTrack '%s' created with %d files",
                        savedTrack.getTitle(), savedTrack.getFiles().size()));

        log.info("SoundTrack created successfully with ID: {}", savedTrack.getId());
        return mapToResponse(savedTrack);
    }

    /**
     * GET ALL - Retrieve all SoundTracks
     */
    @Transactional(readOnly = true)
    public List<Response> getAllSoundTracks() {
        log.info("Fetching all SoundTracks");

        return soundTrackRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * UPDATE - Update existing SoundTrack (partial update)
     * Can replace audio files or keep existing ones
     */
    @Transactional
    public Response updateSoundTrack(Long id, UpdateRequest request, List<MultipartFile> audioFiles,
                                     MultipartFile coverImage) {
        log.info("Updating SoundTrack with ID: {}", id);

        SoundTrack soundTrack = soundTrackRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SoundTrack not found with id: " + id));

        // Track changes for logging
        Map<String, Object> changes = new HashMap<>();

        // Update fields only if present
        if (request.getTitle() != null && !request.getTitle().equals(soundTrack.getTitle())) {
            changes.put("title", Map.of("old", soundTrack.getTitle(), "new", request.getTitle()));
            soundTrack.setTitle(request.getTitle());
        }

        // Update cover image if provided
        if (coverImage != null && !coverImage.isEmpty()) {
            try {
                String newCoverUrl = s3Service.upload(
                        coverImage.getBytes(),
                        coverImage.getOriginalFilename(),
                        coverImage.getContentType()
                );
                soundTrack.setCoverUrl(newCoverUrl);
                changes.put("coverUrl", "Updated");
                log.info("Cover image updated: {}", newCoverUrl);
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload cover image", e);
            }
        }

        if (request.getDescription() != null) {
            soundTrack.setDescription(request.getDescription());
        }

        if (request.getReading() != null) {
            soundTrack.setReading(request.getReading());
        }

        if (request.getSoundType() != null && request.getSoundType() != soundTrack.getSoundType()) {
            changes.put("soundType", Map.of("old", soundTrack.getSoundType(), "new", request.getSoundType()));
            soundTrack.setSoundType(request.getSoundType());
        }

        if (request.getLanguage() != null) {
            soundTrack.setLanguage(request.getLanguage());
        }

        // CHANGED: Update locations collection
        if (request.getLocations() != null) {
            soundTrack.setLocations(request.getLocations());
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

        if (request.getKeywords() != null) {
            soundTrack.setKeywords(request.getKeywords());
        }

        if (request.getTags() != null) {
            soundTrack.setTags(request.getTags());
        }

        // If new audio files are provided, replace all files
        if (audioFiles != null && !audioFiles.isEmpty()) {
            // Validate SINGLE state
            if (soundTrack.getTrackState() == TrackState.SINGLE && audioFiles.size() > 1) {
                throw new IllegalArgumentException("SINGLE track state can only have 1 audio file");
            }

            soundTrack.getFiles().clear();

            for (int i = 0; i < audioFiles.size(); i++) {
                MultipartFile audioFile = audioFiles.get(i);
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
                            .fileType(fileType)
                            .durationSeconds(0)
                            .sizeBytes(audioFile.getSize())
                            .readerName(readerName)
                            .build();

                    soundTrack.addFile(trackFile);
                    log.info("Audio file {} uploaded to S3: {}", i + 1, audioUrl);

                } catch (IOException e) {
                    throw new RuntimeException("Failed to upload audio file: " + audioFile.getOriginalFilename(), e);
                }
            }

            changes.put("files", String.format("Replaced with %d new files", audioFiles.size()));
        }

        SoundTrack updatedTrack = soundTrackRepository.save(soundTrack);

        // Log to SoundTrackLog table
        if (!changes.isEmpty()) {
            try {
                String changesJson = objectMapper.writeValueAsString(changes);
                logAction(updatedTrack, "UPDATED", changesJson);
            } catch (Exception e) {
                log.error("Failed to serialize changes", e);
                logAction(updatedTrack, "UPDATED", "SoundTrack updated");
            }
        }

        log.info("SoundTrack updated successfully with ID: {}", id);
        return mapToResponse(updatedTrack);
    }

    /**
     * DELETE - Remove SoundTrack
     */
    @Transactional
    public void deleteSoundTrack(Long id) {
        log.info("Deleting SoundTrack with ID: {}", id);

        SoundTrack soundTrack = soundTrackRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SoundTrack not found with id: " + id));

        String trackTitle = soundTrack.getTitle();

        // Log to SoundTrackLog table BEFORE deletion
        logAction(soundTrack, "DELETED", String.format("SoundTrack '%s' deleted", trackTitle));

        soundTrackRepository.delete(soundTrack);

        log.info("SoundTrack deleted successfully with ID: {}", id);
    }

    /**
     * SEARCH BY TAG - Find tracks by tag
     */
    @Transactional(readOnly = true)
    public List<Response> searchByTag(String tag) {
        log.info("Searching SoundTracks by tag: {}", tag);

        return soundTrackRepository.findAll().stream()
                .filter(track -> track.getTags().stream()
                        .anyMatch(t -> t.equalsIgnoreCase(tag)))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * SEARCH BY KEYWORD - Find tracks by keyword
     */
    @Transactional(readOnly = true)
    public List<Response> searchByKeyword(String keyword) {
        log.info("Searching SoundTracks by keyword: {}", keyword);

        return soundTrackRepository.findAll().stream()
                .filter(track -> track.getKeywords().stream()
                        .anyMatch(k -> k.equalsIgnoreCase(keyword)))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * SEARCH BY LOCATION - Find tracks by location (NEW)
     */
    @Transactional(readOnly = true)
    public List<Response> searchByLocation(String location) {
        log.info("Searching SoundTracks by location: {}", location);

        return soundTrackRepository.findAll().stream()
                .filter(track -> track.getLocations().stream()
                        .anyMatch(loc -> loc.equalsIgnoreCase(location)))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ============ PRIVATE HELPER METHODS ============

    /**
     * Determine FileType from MultipartFile
     */
    private FileType determineFileType(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        if (contentType != null) {
            if (contentType.contains("mp3") || (filename != null && filename.endsWith(".mp3"))) {
                return FileType.MP3;
            }
            if (contentType.contains("wav") || (filename != null && filename.endsWith(".wav"))) {
                return FileType.WAV;
            }
            if (contentType.contains("ogg") || (filename != null && filename.endsWith(".ogg"))) {
                return FileType.OGG;
            }
            if (contentType.contains("aac") || (filename != null && filename.endsWith(".aac"))) {
                return FileType.AAC;
            }
            if (contentType.contains("flac") || (filename != null && filename.endsWith(".flac"))) {
                return FileType.FLAC;
            }
        }

        return FileType.OTHER;
    }

    /**
     * Map SoundTrack entity to Response DTO
     */
    private Response mapToResponse(SoundTrack soundTrack) {
        List<FileResponse> fileDTOs = soundTrack.getFiles().stream()
                .map(file -> FileResponse.builder()
                        .id(file.getId())
                        .fileUrl(file.getFileUrl())
                        .fileType(file.getFileType())
                        .durationSeconds(file.getDurationSeconds())
                        .sizeBytes(file.getSizeBytes())
                        .readerName(file.getReaderName())
                        .build())
                .collect(Collectors.toList());

        long totalDuration = soundTrack.getFiles().stream()
                .mapToLong(SoundTrackFile::getDurationSeconds)
                .sum();

        long totalSize = soundTrack.getFiles().stream()
                .mapToLong(SoundTrackFile::getSizeBytes)
                .sum();

        return Response.builder()
                .id(soundTrack.getId())
                .title(soundTrack.getTitle())
                .coverUrl(soundTrack.getCoverUrl())
                .description(soundTrack.getDescription())
                .reading(soundTrack.getReading())
                .soundType(soundTrack.getSoundType())
                .language(soundTrack.getLanguage())
                .locations(soundTrack.getLocations()) // CHANGED
                .director(soundTrack.getDirector())
                .isThisProjectOfInstitute(soundTrack.isThisProjectOfInstitute())
                .trackState(soundTrack.getTrackState())
                .keywords(soundTrack.getKeywords())
                .tags(soundTrack.getTags())
                .files(fileDTOs)
                .totalDurationSeconds(totalDuration)
                .totalSizeBytes(totalSize)
                .createdAt(soundTrack.getCreatedAt())
                .updatedAt(soundTrack.getUpdatedAt())
                .build();
    }

    /**
     * Log action to SoundTrackLog table
     */
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
        log.debug("Logged action '{}' for SoundTrack ID: {}", action, soundTrack.getId());
    }
}