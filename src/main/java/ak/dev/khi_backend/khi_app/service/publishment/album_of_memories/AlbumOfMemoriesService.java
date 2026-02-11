package ak.dev.khi_backend.khi_app.service.publishment.album_of_memories;


import ak.dev.khi_backend.khi_app.dto.publishment.album_of_memories.AlbumDto;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.model.publishment.album_of_memories.AlbumAuditLog;
import ak.dev.khi_backend.khi_app.model.publishment.album_of_memories.AlbumContent;
import ak.dev.khi_backend.khi_app.model.publishment.album_of_memories.AlbumMedia;
import ak.dev.khi_backend.khi_app.model.publishment.album_of_memories.AlbumOfMemories;
import ak.dev.khi_backend.khi_app.repository.publishment.album_of_memories.AlbumAuditLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.album_of_memories.AlbumOfMemoriesRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumOfMemoriesService {

    private final AlbumOfMemoriesRepository albumRepository;
    private final AlbumAuditLogRepository auditLogRepository;
    private final S3Service s3Service;

    /**
     * ✅ ADD ALBUM - Create a new album with file uploads
     */
    @Transactional
    public AlbumDto addAlbum(AlbumDto dto, MultipartFile coverImage,
                             List<MultipartFile> mediaFiles, MultipartFile attachment) {
        log.info("🎵 Creating album: {}/{}",
                dto.getCkbContent() != null ? dto.getCkbContent().getTitle() : "N/A",
                dto.getKmrContent() != null ? dto.getKmrContent().getTitle() : "N/A");

        try {
            // Upload cover image
            if (coverImage == null || coverImage.isEmpty()) {
                throw new BadRequestException("cover.required", "Cover image is required");
            }

            log.debug("📤 Uploading cover image to S3: {}", coverImage.getOriginalFilename());
            String coverUrl = s3Service.upload(
                    coverImage.getBytes(),
                    coverImage.getOriginalFilename(),
                    coverImage.getContentType()
            );
            log.info("✅ Cover image uploaded: {}", coverUrl);

            // Build album entity
            AlbumOfMemories album = AlbumOfMemories.builder()
                    .coverUrl(coverUrl)
                    .albumType(dto.getAlbumType())
                    .fileFormat(dto.getFileFormat())
                    .cdNumber(dto.getCdNumber())
                    .numberOfTracks(dto.getNumberOfTracks())
                    .yearOfPublishment(dto.getYearOfPublishment())
                    .contentLanguages(Optional.ofNullable(dto.getContentLanguages()).orElse(new LinkedHashSet<>()))
                    .tagsCkb(Optional.ofNullable(dto.getTags()).map(t -> t.getCkb()).orElse(new LinkedHashSet<>()))
                    .tagsKmr(Optional.ofNullable(dto.getTags()).map(t -> t.getKmr()).orElse(new LinkedHashSet<>()))
                    .keywordsCkb(Optional.ofNullable(dto.getKeywords()).map(k -> k.getCkb()).orElse(new LinkedHashSet<>()))
                    .keywordsKmr(Optional.ofNullable(dto.getKeywords()).map(k -> k.getKmr()).orElse(new LinkedHashSet<>()))
                    .build();

            // Set bilingual content
            setBilingualContent(album, dto);

            // Upload and attach media files (audio/video tracks)
            if (mediaFiles != null && !mediaFiles.isEmpty()) {
                log.info("📤 Uploading {} media files to S3", mediaFiles.size());
                List<AlbumMedia> mediaList = processMediaFiles(mediaFiles, album, dto.getMedia());
                album.setMedia(mediaList);
                log.info("✅ {} media files uploaded", mediaList.size());
            }

            // Upload attachment if provided
            if (attachment != null && !attachment.isEmpty()) {
                log.debug("📤 Uploading attachment to S3: {}", attachment.getOriginalFilename());
                String attachmentUrl = s3Service.upload(
                        attachment.getBytes(),
                        attachment.getOriginalFilename(),
                        attachment.getContentType()
                );
                album.setAttachmentUrl(attachmentUrl);
                album.setAttachmentType(getFileExtension(attachment.getOriginalFilename()));
                log.info("✅ Attachment uploaded: {}", attachmentUrl);
            }

            AlbumOfMemories saved = albumRepository.save(album);
            log.info("✅ Album created successfully - ID: {}", saved.getId());

            createAuditLog(saved, "CREATE", "Album created with S3 media");

            return convertToDto(saved);

        } catch (IOException e) {
            log.error("❌ S3 upload failed", e);
            throw new BadRequestException("media.upload.failed", "Failed to upload files to S3");
        }
    }

    /**
     * ✅ GET ALL ALBUMS - With lazy-loaded media initialized
     */
    @Transactional(readOnly = true)
    public List<AlbumDto> getAllAlbums() {
        log.info("📋 Fetching all albums");
        List<AlbumOfMemories> albums = albumRepository.findAllOrderedByYear();
        log.info("✅ Retrieved {} albums", albums.size());

        return albums.stream()
                .map(album -> {
                    album.getMedia().size(); // Force initialization
                    return convertToDto(album);
                })
                .collect(Collectors.toList());
    }

    /**
     * ✅ SEARCH BY KEYWORD (Language-specific)
     */
    @Transactional(readOnly = true)
    public List<AlbumDto> searchByKeyword(String keyword, String language) {
        log.info("🔍 Searching albums by keyword: '{}' in language: {}", keyword, language);

        if (keyword == null || keyword.trim().isEmpty()) {
            log.warn("⚠️ Empty keyword provided for search");
            return Collections.emptyList();
        }

        List<AlbumOfMemories> results;
        if ("ckb".equalsIgnoreCase(language)) {
            results = albumRepository.searchByKeywordCkb(keyword.trim());
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = albumRepository.searchByKeywordKmr(keyword.trim());
        } else {
            // Search both languages
            Set<AlbumOfMemories> combinedResults = new HashSet<>();
            combinedResults.addAll(albumRepository.searchByKeywordCkb(keyword.trim()));
            combinedResults.addAll(albumRepository.searchByKeywordKmr(keyword.trim()));
            results = new ArrayList<>(combinedResults);
            // Sort by year
            results.sort((a, b) -> {
                int yearCompare = Integer.compare(
                        b.getYearOfPublishment() != null ? b.getYearOfPublishment() : 0,
                        a.getYearOfPublishment() != null ? a.getYearOfPublishment() : 0
                );
                return yearCompare != 0 ? yearCompare : b.getCreatedAt().compareTo(a.getCreatedAt());
            });
        }

        log.info("✅ Found {} albums matching keyword '{}'", results.size(), keyword);

        return results.stream()
                .map(album -> {
                    album.getMedia().size();
                    return convertToDto(album);
                })
                .collect(Collectors.toList());
    }

    /**
     * ✅ SEARCH BY TAG (Language-specific)
     */
    @Transactional(readOnly = true)
    public List<AlbumDto> searchByTag(String tag, String language) {
        log.info("🏷️ Searching albums by tag: '{}' in language: {}", tag, language);

        if (tag == null || tag.trim().isEmpty()) {
            log.warn("⚠️ Empty tag provided for search");
            return Collections.emptyList();
        }

        List<AlbumOfMemories> results;
        if ("ckb".equalsIgnoreCase(language)) {
            results = albumRepository.findByTagCkb(tag.trim());
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = albumRepository.findByTagKmr(tag.trim());
        } else {
            // Search both languages
            Set<AlbumOfMemories> combinedResults = new HashSet<>();
            combinedResults.addAll(albumRepository.findByTagCkb(tag.trim()));
            combinedResults.addAll(albumRepository.findByTagKmr(tag.trim()));
            results = new ArrayList<>(combinedResults);
            results.sort((a, b) -> {
                int yearCompare = Integer.compare(
                        b.getYearOfPublishment() != null ? b.getYearOfPublishment() : 0,
                        a.getYearOfPublishment() != null ? a.getYearOfPublishment() : 0
                );
                return yearCompare != 0 ? yearCompare : b.getCreatedAt().compareTo(a.getCreatedAt());
            });
        }

        log.info("✅ Found {} albums with tag '{}'", results.size(), tag);

        return results.stream()
                .map(album -> {
                    album.getMedia().size();
                    return convertToDto(album);
                })
                .collect(Collectors.toList());
    }

    /**
     * ✅ UPDATE ALBUM
     */
    @Transactional
    public AlbumDto updateAlbum(Long albumId, AlbumDto dto, MultipartFile newCoverImage,
                                List<MultipartFile> newMediaFiles, MultipartFile newAttachment) {
        log.info("✏️ Updating album - ID: {}", albumId);

        AlbumOfMemories album = albumRepository.findById(albumId)
                .orElseThrow(() -> new EntityNotFoundException("Album not found: " + albumId));

        try {
            // Update cover image if provided
            if (newCoverImage != null && !newCoverImage.isEmpty()) {
                String newCoverUrl = s3Service.upload(
                        newCoverImage.getBytes(),
                        newCoverImage.getOriginalFilename(),
                        newCoverImage.getContentType()
                );
                album.setCoverUrl(newCoverUrl);
                log.info("✅ Cover image updated: {}", newCoverUrl);
            }

            // Update metadata
            if (dto.getAlbumType() != null) {
                album.setAlbumType(dto.getAlbumType());
            }
            if (dto.getFileFormat() != null) {
                album.setFileFormat(dto.getFileFormat());
            }
            if (dto.getCdNumber() != null) {
                album.setCdNumber(dto.getCdNumber());
            }
            if (dto.getNumberOfTracks() != null) {
                album.setNumberOfTracks(dto.getNumberOfTracks());
            }
            if (dto.getYearOfPublishment() != null) {
                album.setYearOfPublishment(dto.getYearOfPublishment());
            }

            // Update content languages
            if (dto.getContentLanguages() != null) {
                album.getContentLanguages().clear();
                album.getContentLanguages().addAll(dto.getContentLanguages());
            }

            // Update bilingual content
            updateBilingualContent(album, dto);

            // Update tags and keywords
            updateCollections(album, dto);

            // Update media if provided
            if (newMediaFiles != null && !newMediaFiles.isEmpty()) {
                album.getMedia().clear();
                List<AlbumMedia> newMedia = processMediaFiles(newMediaFiles, album, dto.getMedia());
                album.getMedia().addAll(newMedia);
            }

            // Update attachment if provided
            if (newAttachment != null && !newAttachment.isEmpty()) {
                String attachmentUrl = s3Service.upload(
                        newAttachment.getBytes(),
                        newAttachment.getOriginalFilename(),
                        newAttachment.getContentType()
                );
                album.setAttachmentUrl(attachmentUrl);
                album.setAttachmentType(getFileExtension(newAttachment.getOriginalFilename()));
                log.info("✅ Attachment updated: {}", attachmentUrl);
            }

            AlbumOfMemories updated = albumRepository.save(album);
            log.info("✅ Album updated successfully - ID: {}", updated.getId());

            createAuditLog(updated, "UPDATE", "Album updated");

            return convertToDto(updated);

        } catch (IOException e) {
            log.error("❌ S3 upload failed during update", e);
            throw new BadRequestException("media.upload.failed", "Failed to upload files to S3");
        }
    }

    /**
     * ✅ DELETE ALBUM
     */
    @Transactional
    public void deleteAlbum(Long albumId) {
        log.info("🗑️ Deleting album - ID: {}", albumId);

        AlbumOfMemories album = albumRepository.findById(albumId)
                .orElseThrow(() -> new EntityNotFoundException("Album not found: " + albumId));

        createAuditLog(album, "DELETE", "Album deleted");
        albumRepository.delete(album);
        log.info("✅ Album deleted successfully - ID: {}", albumId);
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private void setBilingualContent(AlbumOfMemories album, AlbumDto dto) {
        if (dto.getCkbContent() != null) {
            album.setCkbContent(AlbumContent.builder()
                    .title(dto.getCkbContent().getTitle())
                    .description(dto.getCkbContent().getDescription())
                    .location(dto.getCkbContent().getLocation())
                    .build());
        }

        if (dto.getKmrContent() != null) {
            album.setKmrContent(AlbumContent.builder()
                    .title(dto.getKmrContent().getTitle())
                    .description(dto.getKmrContent().getDescription())
                    .location(dto.getKmrContent().getLocation())
                    .build());
        }
    }

    private void updateBilingualContent(AlbumOfMemories album, AlbumDto dto) {
        if (dto.getCkbContent() != null) {
            if (album.getCkbContent() == null) {
                album.setCkbContent(new AlbumContent());
            }
            if (dto.getCkbContent().getTitle() != null) {
                album.getCkbContent().setTitle(dto.getCkbContent().getTitle());
            }
            if (dto.getCkbContent().getDescription() != null) {
                album.getCkbContent().setDescription(dto.getCkbContent().getDescription());
            }
            if (dto.getCkbContent().getLocation() != null) {
                album.getCkbContent().setLocation(dto.getCkbContent().getLocation());
            }
        }

        if (dto.getKmrContent() != null) {
            if (album.getKmrContent() == null) {
                album.setKmrContent(new AlbumContent());
            }
            if (dto.getKmrContent().getTitle() != null) {
                album.getKmrContent().setTitle(dto.getKmrContent().getTitle());
            }
            if (dto.getKmrContent().getDescription() != null) {
                album.getKmrContent().setDescription(dto.getKmrContent().getDescription());
            }
            if (dto.getKmrContent().getLocation() != null) {
                album.getKmrContent().setLocation(dto.getKmrContent().getLocation());
            }
        }
    }

    private void updateCollections(AlbumOfMemories album, AlbumDto dto) {
        if (dto.getTags() != null) {
            if (dto.getTags().getCkb() != null) {
                album.getTagsCkb().clear();
                album.getTagsCkb().addAll(dto.getTags().getCkb());
            }
            if (dto.getTags().getKmr() != null) {
                album.getTagsKmr().clear();
                album.getTagsKmr().addAll(dto.getTags().getKmr());
            }
        }

        if (dto.getKeywords() != null) {
            if (dto.getKeywords().getCkb() != null) {
                album.getKeywordsCkb().clear();
                album.getKeywordsCkb().addAll(dto.getKeywords().getCkb());
            }
            if (dto.getKeywords().getKmr() != null) {
                album.getKeywordsKmr().clear();
                album.getKeywordsKmr().addAll(dto.getKeywords().getKmr());
            }
        }
    }

    private List<AlbumMedia> processMediaFiles(List<MultipartFile> files, AlbumOfMemories album,
                                               List<AlbumDto.MediaDto> mediaDtos) throws IOException {
        List<AlbumMedia> mediaList = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file.isEmpty()) continue;

            String mediaUrl = s3Service.upload(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType()
            );

            // Get additional metadata from DTO if provided
            AlbumDto.MediaDto mediaDto = (mediaDtos != null && i < mediaDtos.size())
                    ? mediaDtos.get(i) : null;

            AlbumMedia media = AlbumMedia.builder()
                    .url(mediaUrl)
                    .trackNumber(mediaDto != null ? mediaDto.getTrackNumber() : i + 1)
                    .trackTitleCkb(mediaDto != null ? mediaDto.getTrackTitleCkb() : null)
                    .trackTitleKmr(mediaDto != null ? mediaDto.getTrackTitleKmr() : null)
                    .durationSeconds(mediaDto != null ? mediaDto.getDurationSeconds() : null)
                    .fileFormat(getFileExtension(file.getOriginalFilename()))
                    .fileSizeBytes(file.getSize())
                    .album(album)
                    .build();

            mediaList.add(media);
        }

        return mediaList;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private void createAuditLog(AlbumOfMemories album, String action, String note) {
        AlbumAuditLog auditLog = AlbumAuditLog.builder()
                .album(album)
                .action(action)
                .performedBy("system")
                .note(note)
                .build();
        auditLogRepository.save(auditLog);
    }

    private AlbumDto convertToDto(AlbumOfMemories album) {
        AlbumDto dto = AlbumDto.builder()
                .id(album.getId())
                .coverUrl(album.getCoverUrl())
                .albumType(album.getAlbumType())
                .fileFormat(album.getFileFormat())
                .cdNumber(album.getCdNumber())
                .numberOfTracks(album.getNumberOfTracks())
                .yearOfPublishment(album.getYearOfPublishment())
                .contentLanguages(new LinkedHashSet<>(album.getContentLanguages()))
                .createdAt(album.getCreatedAt())
                .updatedAt(album.getUpdatedAt())
                .build();

        // CKB Content
        if (album.getCkbContent() != null) {
            dto.setCkbContent(AlbumDto.LanguageContentDto.builder()
                    .title(album.getCkbContent().getTitle())
                    .description(album.getCkbContent().getDescription())
                    .location(album.getCkbContent().getLocation())
                    .build());
        }

        // KMR Content
        if (album.getKmrContent() != null) {
            dto.setKmrContent(AlbumDto.LanguageContentDto.builder()
                    .title(album.getKmrContent().getTitle())
                    .description(album.getKmrContent().getDescription())
                    .location(album.getKmrContent().getLocation())
                    .build());
        }

        // Tags
        dto.setTags(AlbumDto.BilingualSet.builder()
                .ckb(new LinkedHashSet<>(album.getTagsCkb()))
                .kmr(new LinkedHashSet<>(album.getTagsKmr()))
                .build());

        // Keywords
        dto.setKeywords(AlbumDto.BilingualSet.builder()
                .ckb(new LinkedHashSet<>(album.getKeywordsCkb()))
                .kmr(new LinkedHashSet<>(album.getKeywordsKmr()))
                .build());

        // Media files
        if (album.getMedia() != null && !album.getMedia().isEmpty()) {
            List<AlbumDto.MediaDto> mediaDtos = album.getMedia().stream()
                    .map(media -> AlbumDto.MediaDto.builder()
                            .id(media.getId())
                            .url(media.getUrl())
                            .trackTitleCkb(media.getTrackTitleCkb())
                            .trackTitleKmr(media.getTrackTitleKmr())
                            .trackNumber(media.getTrackNumber())
                            .durationSeconds(media.getDurationSeconds())
                            .fileFormat(media.getFileFormat())
                            .fileSizeBytes(media.getFileSizeBytes())
                            .createdAt(media.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());
            dto.setMedia(mediaDtos);
        }

        // Attachment
        if (album.getAttachmentUrl() != null) {
            dto.setAttachment(AlbumDto.AttachmentDto.builder()
                    .url(album.getAttachmentUrl())
                    .type(album.getAttachmentType())
                    .build());
        }

        return dto;
    }
}