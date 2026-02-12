package ak.dev.khi_backend.khi_app.service.publishment.album_of_memories;

import ak.dev.khi_backend.khi_app.dto.publishment.album_of_memories.AlbumDto;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import ak.dev.khi_backend.khi_app.model.publishment.album_of_memories.*;
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

    // ============================================================
    // CREATE
    // ============================================================

    /**
     * ✅ ADD ALBUM - Create a new album with optional tracks (upload or links) + optional attachment (upload or links)
     */
    @Transactional
    public AlbumDto addAlbum(
            AlbumDto dto,
            MultipartFile coverImage,
            List<MultipartFile> mediaFiles,
            MultipartFile attachment
    ) {
        log.info("🎵 Creating album: {}/{}",
                dto.getCkbContent() != null ? dto.getCkbContent().getTitle() : "N/A",
                dto.getKmrContent() != null ? dto.getKmrContent().getTitle() : "N/A");

        validateAlbumDto(dto);

        try {
            // cover is required (you can also change this rule if you want coverUrl allowed)
            if (coverImage == null || coverImage.isEmpty()) {
                throw new BadRequestException("cover.required", "Cover image is required");
            }

            String coverUrl = s3Service.upload(
                    coverImage.getBytes(),
                    coverImage.getOriginalFilename(),
                    coverImage.getContentType()
            );

            AlbumOfMemories album = AlbumOfMemories.builder()
                    .coverUrl(coverUrl)
                    .albumType(dto.getAlbumType())
                    .fileFormat(dto.getFileFormat())
                    .cdNumber(dto.getCdNumber())
                    .numberOfTracks(dto.getNumberOfTracks())
                    .yearOfPublishment(dto.getYearOfPublishment())
                    .contentLanguages(Optional.ofNullable(dto.getContentLanguages()).orElse(new LinkedHashSet<>()))
                    .tagsCkb(Optional.ofNullable(dto.getTags()).map(AlbumDto.BilingualSet::getCkb).orElse(new LinkedHashSet<>()))
                    .tagsKmr(Optional.ofNullable(dto.getTags()).map(AlbumDto.BilingualSet::getKmr).orElse(new LinkedHashSet<>()))
                    .keywordsCkb(Optional.ofNullable(dto.getKeywords()).map(AlbumDto.BilingualSet::getCkb).orElse(new LinkedHashSet<>()))
                    .keywordsKmr(Optional.ofNullable(dto.getKeywords()).map(AlbumDto.BilingualSet::getKmr).orElse(new LinkedHashSet<>()))
                    .build();

            setBilingualContent(album, dto);

            // ✅ 1) Tracks from uploads (optional)
            List<AlbumMedia> tracks = new ArrayList<>();
            if (mediaFiles != null && !mediaFiles.isEmpty()) {
                tracks.addAll(processMediaFiles(mediaFiles, album, dto.getMedia()));
            }

            // ✅ 2) Tracks from DTO links (optional)
            attachMediaLinksFromDto(album, dto.getMedia(), tracks);

            if (!tracks.isEmpty()) {
                album.setMedia(tracks);
            }

            // ✅ Attachment: allow upload OR dto.attachment link/embed/external
            applyAttachment(album, dto.getAttachment(), attachment);

            AlbumOfMemories saved = albumRepository.save(album);
            createAuditLog(saved, "CREATE", "Album created (uploads + links supported)");

            return convertToDto(saved);

        } catch (IOException e) {
            log.error("❌ S3 upload failed", e);
            throw new BadRequestException("media.upload.failed", "Failed to upload files to S3");
        }
    }

    // ============================================================
    // GET ALL
    // ============================================================

    @Transactional(readOnly = true)
    public List<AlbumDto> getAllAlbums() {
        log.info("📋 Fetching all albums");
        List<AlbumOfMemories> albums = albumRepository.findAllOrderedByYear();

        return albums.stream()
                .map(album -> {
                    album.getMedia().size(); // init
                    return convertToDto(album);
                })
                .collect(Collectors.toList());
    }

    // ============================================================
    // SEARCH
    // ============================================================

    @Transactional(readOnly = true)
    public List<AlbumDto> searchByKeyword(String keyword, String language) {
        log.info("🔍 Searching albums by keyword: '{}' in language: {}", keyword, language);

        if (keyword == null || keyword.trim().isEmpty()) return Collections.emptyList();

        List<AlbumOfMemories> results;
        if ("ckb".equalsIgnoreCase(language)) {
            results = albumRepository.searchByKeywordCkb(keyword.trim());
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = albumRepository.searchByKeywordKmr(keyword.trim());
        } else {
            Set<AlbumOfMemories> combined = new HashSet<>();
            combined.addAll(albumRepository.searchByKeywordCkb(keyword.trim()));
            combined.addAll(albumRepository.searchByKeywordKmr(keyword.trim()));
            results = new ArrayList<>(combined);
            sortAlbums(results);
        }

        return results.stream()
                .map(album -> {
                    album.getMedia().size();
                    return convertToDto(album);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AlbumDto> searchByTag(String tag, String language) {
        log.info("🏷️ Searching albums by tag: '{}' in language: {}", tag, language);

        if (tag == null || tag.trim().isEmpty()) return Collections.emptyList();

        List<AlbumOfMemories> results;
        if ("ckb".equalsIgnoreCase(language)) {
            results = albumRepository.findByTagCkb(tag.trim());
        } else if ("kmr".equalsIgnoreCase(language)) {
            results = albumRepository.findByTagKmr(tag.trim());
        } else {
            Set<AlbumOfMemories> combined = new HashSet<>();
            combined.addAll(albumRepository.findByTagCkb(tag.trim()));
            combined.addAll(albumRepository.findByTagKmr(tag.trim()));
            results = new ArrayList<>(combined);
            sortAlbums(results);
        }

        return results.stream()
                .map(album -> {
                    album.getMedia().size();
                    return convertToDto(album);
                })
                .collect(Collectors.toList());
    }

    private void sortAlbums(List<AlbumOfMemories> results) {
        results.sort((a, b) -> {
            int yearCompare = Integer.compare(
                    b.getYearOfPublishment() != null ? b.getYearOfPublishment() : 0,
                    a.getYearOfPublishment() != null ? a.getYearOfPublishment() : 0
            );
            return yearCompare != 0 ? yearCompare : b.getCreatedAt().compareTo(a.getCreatedAt());
        });
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @Transactional
    public AlbumDto updateAlbum(
            Long albumId,
            AlbumDto dto,
            MultipartFile newCoverImage,
            List<MultipartFile> newMediaFiles,
            MultipartFile newAttachment
    ) {
        log.info("✏️ Updating album - ID: {}", albumId);

        AlbumOfMemories album = albumRepository.findById(albumId)
                .orElseThrow(() -> new EntityNotFoundException("Album not found: " + albumId));

        validateAlbumDto(dto);

        try {
            // cover optional on update
            if (newCoverImage != null && !newCoverImage.isEmpty()) {
                String newCoverUrl = s3Service.upload(
                        newCoverImage.getBytes(),
                        newCoverImage.getOriginalFilename(),
                        newCoverImage.getContentType()
                );
                album.setCoverUrl(newCoverUrl);
            }

            // metadata
            if (dto.getAlbumType() != null) album.setAlbumType(dto.getAlbumType());
            if (dto.getFileFormat() != null) album.setFileFormat(dto.getFileFormat());
            if (dto.getCdNumber() != null) album.setCdNumber(dto.getCdNumber());
            if (dto.getNumberOfTracks() != null) album.setNumberOfTracks(dto.getNumberOfTracks());
            if (dto.getYearOfPublishment() != null) album.setYearOfPublishment(dto.getYearOfPublishment());

            // languages
            if (dto.getContentLanguages() != null) {
                album.getContentLanguages().clear();
                album.getContentLanguages().addAll(dto.getContentLanguages());
            }

            // content
            updateBilingualContent(album, dto);

            // tags/keywords
            updateCollections(album, dto);

            // ✅ TRACKS UPDATE RULE:
            // if user sends new uploads OR sends dto.media (even only links) => replace tracks with combined list
            boolean hasUploads = newMediaFiles != null && newMediaFiles.stream().anyMatch(f -> f != null && !f.isEmpty());
            boolean hasDtoMedia = dto.getMedia() != null; // if provided -> user wants to change tracks

            if (hasUploads || hasDtoMedia) {
                album.getMedia().clear();

                List<AlbumMedia> tracks = new ArrayList<>();

                if (hasUploads) {
                    tracks.addAll(processMediaFiles(newMediaFiles, album, dto.getMedia()));
                }

                attachMediaLinksFromDto(album, dto.getMedia(), tracks);

                album.getMedia().addAll(tracks);
            }

            // ✅ Attachment update: upload OR dto.attachment links
            applyAttachment(album, dto.getAttachment(), newAttachment);

            AlbumOfMemories updated = albumRepository.save(album);
            createAuditLog(updated, "UPDATE", "Album updated (uploads + links supported)");

            return convertToDto(updated);

        } catch (IOException e) {
            log.error("❌ S3 upload failed during update", e);
            throw new BadRequestException("media.upload.failed", "Failed to upload files to S3");
        }
    }

    // ============================================================
    // DELETE
    // ============================================================

    @Transactional
    public void deleteAlbum(Long albumId) {
        AlbumOfMemories album = albumRepository.findById(albumId)
                .orElseThrow(() -> new EntityNotFoundException("Album not found: " + albumId));
        createAuditLog(album, "DELETE", "Album deleted");
        albumRepository.delete(album);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private void validateAlbumDto(AlbumDto dto) {
        if (dto == null) throw new BadRequestException("album.required", "Album body is required");
        if (dto.getAlbumType() == null) throw new BadRequestException("album.type.required", "albumType is required");
        if (dto.getContentLanguages() == null || dto.getContentLanguages().isEmpty()) {
            throw new BadRequestException("album.languages.required", "contentLanguages is required");
        }
    }

    /**
     * ✅ Tracks from DTO links (url/external/embed)
     * - MEDIA TRACK: must have at least one of: url OR externalUrl OR embedUrl
     */
    private void attachMediaLinksFromDto(AlbumOfMemories album, List<AlbumDto.MediaDto> dtoMedia, List<AlbumMedia> out) {
        if (dtoMedia == null || dtoMedia.isEmpty()) return;

        Set<String> existing = new HashSet<>();
        for (AlbumMedia m : out) {
            String key = mediaKey(m.getUrl(), m.getEmbedUrl(), m.getExternalUrl(), m.getTrackNumber());
            if (key != null) existing.add(key);
        }

        for (int i = 0; i < dtoMedia.size(); i++) {
            AlbumDto.MediaDto m = dtoMedia.get(i);
            if (m == null) continue;

            String url = trimOrNull(m.getUrl());
            String ext = trimOrNull(m.getExternalUrl());
            String emb = trimOrNull(m.getEmbedUrl());

            if (isBlank(url) && isBlank(ext) && isBlank(emb)) {
                // user might send only metadata for uploaded track -> ignore if no link
                continue;
            }

            Integer trackNumber = m.getTrackNumber() != null ? m.getTrackNumber() : (i + 1);

            String key = mediaKey(url, emb, ext, trackNumber);
            if (key != null && existing.contains(key)) continue;

            AlbumMedia media = AlbumMedia.builder()
                    .url(url)
                    .externalUrl(ext)
                    .embedUrl(emb)
                    .trackNumber(trackNumber)
                    .trackTitleCkb(m.getTrackTitleCkb())
                    .trackTitleKmr(m.getTrackTitleKmr())
                    .durationSeconds(m.getDurationSeconds())
                    .fileFormat(m.getFileFormat())
                    .fileSizeBytes(m.getFileSizeBytes())
                    .album(album)
                    .build();

            out.add(media);
            if (key != null) existing.add(key);
        }
    }

    private String mediaKey(String url, String embed, String external, Integer trackNo) {
        String best = trimOrNull(url);
        if (best == null) best = trimOrNull(embed);
        if (best == null) best = trimOrNull(external);
        if (best == null) return null;
        return (trackNo != null ? trackNo : 0) + "|" + best;
    }

    /**
     * ✅ Attachment can be:
     * - uploaded file (attachment param) OR
     * - dtoAttachment.url/externalUrl/embedUrl
     */
    private void applyAttachment(AlbumOfMemories album, AlbumDto.AttachmentDto dtoAttachment, MultipartFile attachmentFile) throws IOException {
        // upload wins
        if (attachmentFile != null && !attachmentFile.isEmpty()) {
            String url = s3Service.upload(
                    attachmentFile.getBytes(),
                    attachmentFile.getOriginalFilename(),
                    attachmentFile.getContentType()
            );
            album.setAttachmentUrl(url);
            album.setAttachmentExternalUrl(null);
            album.setAttachmentEmbedUrl(null);
            album.setAttachmentType(getFileExtension(attachmentFile.getOriginalFilename()));
            return;
        }

        // dto attachment (optional)
        if (dtoAttachment != null) {
            String url = trimOrNull(dtoAttachment.getUrl());
            String ext = trimOrNull(dtoAttachment.getExternalUrl());
            String emb = trimOrNull(dtoAttachment.getEmbedUrl());

            boolean hasAny = !isBlank(url) || !isBlank(ext) || !isBlank(emb);
            if (hasAny) {
                album.setAttachmentUrl(url);
                album.setAttachmentExternalUrl(ext);
                album.setAttachmentEmbedUrl(emb);
                album.setAttachmentType(trimOrNull(dtoAttachment.getType()));
            }
        }
    }

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
            if (album.getCkbContent() == null) album.setCkbContent(new AlbumContent());
            if (dto.getCkbContent().getTitle() != null) album.getCkbContent().setTitle(dto.getCkbContent().getTitle());
            if (dto.getCkbContent().getDescription() != null) album.getCkbContent().setDescription(dto.getCkbContent().getDescription());
            if (dto.getCkbContent().getLocation() != null) album.getCkbContent().setLocation(dto.getCkbContent().getLocation());
        }

        if (dto.getKmrContent() != null) {
            if (album.getKmrContent() == null) album.setKmrContent(new AlbumContent());
            if (dto.getKmrContent().getTitle() != null) album.getKmrContent().setTitle(dto.getKmrContent().getTitle());
            if (dto.getKmrContent().getDescription() != null) album.getKmrContent().setDescription(dto.getKmrContent().getDescription());
            if (dto.getKmrContent().getLocation() != null) album.getKmrContent().setLocation(dto.getKmrContent().getLocation());
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
            if (file == null || file.isEmpty()) continue;

            String mediaUrl = s3Service.upload(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType()
            );

            AlbumDto.MediaDto mediaDto = (mediaDtos != null && i < mediaDtos.size())
                    ? mediaDtos.get(i) : null;

            AlbumMedia media = AlbumMedia.builder()
                    .url(mediaUrl)
                    .externalUrl(null)
                    .embedUrl(null)
                    .trackNumber(mediaDto != null && mediaDto.getTrackNumber() != null ? mediaDto.getTrackNumber() : i + 1)
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
        if (filename == null || !filename.contains(".")) return null;
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

    // ✅ UPDATED: includes external/embed in response
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

        if (album.getCkbContent() != null) {
            dto.setCkbContent(AlbumDto.LanguageContentDto.builder()
                    .title(album.getCkbContent().getTitle())
                    .description(album.getCkbContent().getDescription())
                    .location(album.getCkbContent().getLocation())
                    .build());
        }

        if (album.getKmrContent() != null) {
            dto.setKmrContent(AlbumDto.LanguageContentDto.builder()
                    .title(album.getKmrContent().getTitle())
                    .description(album.getKmrContent().getDescription())
                    .location(album.getKmrContent().getLocation())
                    .build());
        }

        dto.setTags(AlbumDto.BilingualSet.builder()
                .ckb(new LinkedHashSet<>(album.getTagsCkb()))
                .kmr(new LinkedHashSet<>(album.getTagsKmr()))
                .build());

        dto.setKeywords(AlbumDto.BilingualSet.builder()
                .ckb(new LinkedHashSet<>(album.getKeywordsCkb()))
                .kmr(new LinkedHashSet<>(album.getKeywordsKmr()))
                .build());

        if (album.getMedia() != null && !album.getMedia().isEmpty()) {
            List<AlbumDto.MediaDto> mediaDtos = album.getMedia().stream()
                    .sorted(Comparator.comparingInt(m -> m.getTrackNumber() != null ? m.getTrackNumber() : 0))
                    .map(media -> AlbumDto.MediaDto.builder()
                            .id(media.getId())
                            .url(media.getUrl())
                            .externalUrl(media.getExternalUrl())
                            .embedUrl(media.getEmbedUrl())
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

        // Attachment (updated)
        boolean hasAttach =
                (album.getAttachmentUrl() != null && !album.getAttachmentUrl().isBlank()) ||
                        (album.getAttachmentExternalUrl() != null && !album.getAttachmentExternalUrl().isBlank()) ||
                        (album.getAttachmentEmbedUrl() != null && !album.getAttachmentEmbedUrl().isBlank());

        if (hasAttach) {
            dto.setAttachment(AlbumDto.AttachmentDto.builder()
                    .url(album.getAttachmentUrl())
                    .externalUrl(album.getAttachmentExternalUrl())
                    .embedUrl(album.getAttachmentEmbedUrl())
                    .type(album.getAttachmentType())
                    .build());
        }

        return dto;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
