package ak.dev.khi_backend.khi_app.api.publishment.album_of_memories;

import ak.dev.khi_backend.khi_app.dto.publishment.album_of_memories.AlbumDto;
import ak.dev.khi_backend.khi_app.service.publishment.album_of_memories.AlbumOfMemoriesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/albums")
@RequiredArgsConstructor
public class AlbumOfMemoriesController {

    private final AlbumOfMemoriesService albumService;
    private final ObjectMapper objectMapper;

    /**
     * ✅ CREATE ALBUM
     *
     * Form Data:
     * - album: JSON string with bilingual data
     * - coverImage: Image file (required)
     * - mediaFiles: Array of audio/video files (optional)
     * - attachment: PDF or video for demonstration (optional)
     *
     * Expected JSON format:
     * {
     *   "ckbContent": {"title": "...", "description": "...", "location": "..."},
     *   "kmrContent": {"title": "...", "description": "...", "location": "..."},
     *   "albumType": "AUDIO" or "VIDEO",
     *   "fileFormat": "mp3",
     *   "cdNumber": 1,
     *   "numberOfTracks": 10,
     *   "yearOfPublishment": 2024,
     *   "contentLanguages": ["CKB", "KMR"],
     *   "tags": {"ckb": ["..."], "kmr": ["..."]},
     *   "keywords": {"ckb": ["..."], "kmr": ["..."]}
     * }
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AlbumDto> createAlbum(
            @RequestPart("album") String albumJson,
            @RequestPart("coverImage") MultipartFile coverImage,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment) {

        log.info("🎵 Received request to create album");

        try {
            AlbumDto albumDto = objectMapper.readValue(albumJson, AlbumDto.class);
            AlbumDto created = albumService.addAlbum(albumDto, coverImage, mediaFiles, attachment);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("❌ Failed to create album", e);
            throw new RuntimeException("Failed to create album: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ GET ALL ALBUMS
     * Returns all albums with bilingual content ordered by year (newest first)
     */
    @GetMapping(value = {"", "/", "/all"})
    public ResponseEntity<List<AlbumDto>> getAllAlbums() {
        log.info("📋 Fetching all albums");
        List<AlbumDto> albums = albumService.getAllAlbums();
        return ResponseEntity.ok(albums);
    }

    /**
     * ✅ SEARCH BY KEYWORD (Language-Specific)
     *
     * @param keyword - Search term
     * @param language - "ckb" (Sorani), "kmr" (Kurmanji), or "both" (default: both)
     *
     * Examples:
     * - /api/v1/albums/search/keyword?keyword=گۆرانی&language=ckb
     * - /api/v1/albums/search/keyword?keyword=Stranî&language=kmr
     * - /api/v1/albums/search/keyword?keyword=music (searches both languages)
     */
    @GetMapping("/search/keyword")
    public ResponseEntity<List<AlbumDto>> searchByKeyword(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "both") String language) {

        log.info("🔍 Searching albums by keyword: '{}' in language: {}", keyword, language);
        List<AlbumDto> results = albumService.searchByKeyword(keyword, language);
        return ResponseEntity.ok(results);
    }

    /**
     * ✅ SEARCH BY TAG (Language-Specific)
     *
     * @param tag - Tag to search for
     * @param language - "ckb" (Sorani), "kmr" (Kurmanji), or "both" (default: both)
     *
     * Examples:
     * - /api/v1/albums/search/tag?tag=موزیک&language=ckb
     * - /api/v1/albums/search/tag?tag=Muzîk&language=kmr
     * - /api/v1/albums/search/tag?tag=classic (searches both languages)
     */
    @GetMapping("/search/tag")
    public ResponseEntity<List<AlbumDto>> searchByTag(
            @RequestParam String tag,
            @RequestParam(defaultValue = "both") String language) {

        log.info("🏷️ Searching albums by tag: '{}' in language: {}", tag, language);
        List<AlbumDto> results = albumService.searchByTag(tag, language);
        return ResponseEntity.ok(results);
    }

    /**
     * ✅ UPDATE ALBUM
     *
     * Form Data:
     * - album: JSON string with fields to update
     * - coverImage: New cover image (optional)
     * - mediaFiles: New media files (optional)
     * - attachment: New attachment (optional)
     *
     * All fields in the JSON are optional - only provided fields will be updated
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AlbumDto> updateAlbum(
            @PathVariable Long id,
            @RequestPart("album") String albumJson,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment) {

        log.info("✏️ Updating album - ID: {}", id);

        try {
            AlbumDto albumDto = objectMapper.readValue(albumJson, AlbumDto.class);
            AlbumDto updated = albumService.updateAlbum(id, albumDto, coverImage, mediaFiles, attachment);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("❌ Failed to update album", e);
            throw new RuntimeException("Failed to update album: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ DELETE ALBUM
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlbum(@PathVariable Long id) {
        log.info("🗑️ Deleting album - ID: {}", id);
        albumService.deleteAlbum(id);
        return ResponseEntity.noContent().build();
    }
}