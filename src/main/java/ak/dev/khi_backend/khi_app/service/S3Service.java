package ak.dev.khi_backend.khi_app.service;

import ak.dev.khi_backend.khi_app.enums.project.ProjectMediaType;
import ak.dev.khi_backend.khi_app.exceptions.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.base-folder:khi-web-folders}")
    private String baseFolder;

    @Value("${aws.s3.region}")
    private String region;

    // ============================================================
    // FOLDER NAMES
    // ============================================================
    private static final String FOLDER_IMAGES = "images";
    private static final String FOLDER_VIDEOS = "video";
    private static final String FOLDER_AUDIO = "audio";
    private static final String FOLDER_FILES = "files";
    private static final String FOLDER_ALBUMS = "albums";
    private static final String FOLDER_COVERS = "covers";
    private static final String FOLDER_HOVER = "hover";

    // ============================================================
    // UPLOAD METHODS
    // ============================================================

    /**
     * Upload file with automatic folder detection based on content type
     */
    public String upload(byte[] fileBytes, String originalFilename, String contentType) {
        return upload(fileBytes, originalFilename, contentType, (ProjectMediaType) null);
    }

    /**
     * Upload file with explicit media type (use this when you know the ProjectMediaType)
     */
    public String upload(byte[] fileBytes, String originalFilename, String contentType, ProjectMediaType mediaType) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BadRequestException("media.invalid", "File is empty or null");
        }

        String folder = mediaType != null ? getFolderForMediaType(mediaType) : detectFolder(contentType);
        String key = generateKey(folder, originalFilename);

        log.info("⬆️ Uploading to S3: bucket={}, folder={}, key={}, contentType={}",
                bucket, folder, key, contentType);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(fileBytes));

            String publicUrl = getPublicUrl(key);
            log.info("✅ File uploaded successfully: {}", publicUrl);

            return publicUrl;
        } catch (S3Exception e) {
            log.error("❌ S3 upload failed: {}", e.getMessage(), e);
            throw new BadRequestException("s3.upload.failed", "Failed to upload file to S3: " + e.getMessage());
        }
    }

    /**
     * ✅ Upload album cover image (CKB or KMR)
     */
    public String uploadAlbumCover(byte[] fileBytes, String originalFilename, String contentType, boolean isCkb) {
        String subFolder = isCkb ? "ckb" : "kmr";
        String key = baseFolder + "/" + FOLDER_ALBUMS + "/" + FOLDER_COVERS + "/" + subFolder + "/" +
                UUID.randomUUID() + "-" + sanitizeFilename(originalFilename);

        log.info("⬆️ Uploading album cover ({}): {}", isCkb ? "CKB" : "KMR", key);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(fileBytes));

            String publicUrl = getPublicUrl(key);
            log.info("✅ Album cover uploaded: {}", publicUrl);
            return publicUrl;
        } catch (S3Exception e) {
            log.error("❌ Failed to upload album cover: {}", e.getMessage(), e);
            throw new BadRequestException("s3.upload.failed", "Failed to upload cover: " + e.getMessage());
        }
    }

    /**
     * ✅ Upload album hover image
     */
    public String uploadAlbumHover(byte[] fileBytes, String originalFilename, String contentType) {
        String key = baseFolder + "/" + FOLDER_ALBUMS + "/" + FOLDER_HOVER + "/" +
                UUID.randomUUID() + "-" + sanitizeFilename(originalFilename);

        log.info("⬆️ Uploading album hover image: {}", key);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(fileBytes));

            String publicUrl = getPublicUrl(key);
            log.info("✅ Album hover image uploaded: {}", publicUrl);
            return publicUrl;
        } catch (S3Exception e) {
            log.error("❌ Failed to upload hover image: {}", e.getMessage(), e);
            throw new BadRequestException("s3.upload.failed", "Failed to upload hover: " + e.getMessage());
        }
    }

    // ============================================================
    // DELETE METHODS
    // ============================================================

    /**
     * ✅ Delete file from S3 by full URL
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            log.warn("⚠️ Delete requested with null/blank URL");
            return;
        }

        try {
            String key = extractKeyFromUrl(fileUrl);
            if (key == null) {
                log.warn("⚠️ Could not extract key from URL: {}", fileUrl);
                return;
            }

            deleteByKey(key);
        } catch (Exception e) {
            log.error("❌ Failed to delete file: {}", fileUrl, e);
            // Don't throw - deletion failure shouldn't break business logic
        }
    }

    /**
     * ✅ Delete file from S3 by key
     */
    public void deleteByKey(String key) {
        if (key == null || key.isBlank()) {
            log.warn("⚠️ Delete requested with null/blank key");
            return;
        }

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
            log.info("🗑️ Deleted from S3: bucket={}, key={}", bucket, key);
        } catch (S3Exception e) {
            log.error("❌ S3 delete failed: bucket={}, key={}, error={}", bucket, key, e.getMessage());
            // Don't throw - allow cascade to continue
        }
    }

    /**
     * ✅ Delete multiple files at once
     */
    public void deleteFiles(java.util.List<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) return;

        log.info("🗑️ Batch deleting {} files from S3", fileUrls.size());

        for (String url : fileUrls) {
            deleteFile(url);
        }
    }

    // ============================================================
    // URL & KEY HELPERS
    // ============================================================

    /**
     * Extract S3 key from various URL formats
     */
    public String extractKeyFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return null;

        try {
            URI uri = new URI(fileUrl);
            String path = uri.getPath();

            // Remove leading slash
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            // Handle virtual-hosted style: bucket.s3.region.amazonaws.com/key
            // Handle path-style: s3.region.amazonaws.com/bucket/key

            // If path starts with bucket name, remove it
            if (path.startsWith(bucket + "/")) {
                path = path.substring(bucket.length() + 1);
            }

            // If path starts with baseFolder, keep it (it's part of the key)
            // Key format: khi-web-folders/images/uuid-filename.jpg

            log.debug("Extracted key from URL: {} -> {}", fileUrl, path);
            return path;

        } catch (Exception e) {
            log.warn("⚠️ Failed to parse S3 URL: {}", fileUrl, e);

            // Fallback: try simple string manipulation
            return extractKeyFallback(fileUrl);
        }
    }

    /**
     * Fallback key extraction for non-standard URLs
     */
    private String extractKeyFallback(String fileUrl) {
        // Try to find baseFolder in URL and extract from there
        int baseIndex = fileUrl.indexOf(baseFolder);
        if (baseIndex != -1) {
            String key = fileUrl.substring(baseIndex);
            // Remove query parameters if any
            int queryIndex = key.indexOf("?");
            if (queryIndex != -1) {
                key = key.substring(0, queryIndex);
            }
            return key;
        }

        // Last resort: return everything after the last slash
        int lastSlash = fileUrl.lastIndexOf('/');
        if (lastSlash != -1 && lastSlash < fileUrl.length() - 1) {
            return baseFolder + "/" + FOLDER_FILES + "/" + fileUrl.substring(lastSlash + 1);
        }

        return null;
    }

    /**
     * Get public URL for a key
     */
    public String getPublicUrl(String key) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    /**
     * Check if URL is from our S3 bucket
     */
    public boolean isOurS3Url(String url) {
        if (url == null) return false;
        return url.contains(bucket) && url.contains(".s3.");
    }

    // ============================================================
    // FOLDER DETECTION
    // ============================================================

    private String detectFolder(String contentType) {
        if (contentType == null) return FOLDER_FILES;

        String type = contentType.toLowerCase();

        if (type.startsWith("image/")) return FOLDER_IMAGES;
        if (type.startsWith("video/")) return FOLDER_VIDEOS;
        if (type.startsWith("audio/")) return FOLDER_AUDIO;

        return FOLDER_FILES;
    }

    private String getFolderForMediaType(ProjectMediaType mediaType) {
        if (mediaType == null) return FOLDER_FILES;

        return switch (mediaType) {
            case IMAGE -> FOLDER_IMAGES;
            case VIDEO -> FOLDER_VIDEOS;
            case AUDIO -> FOLDER_AUDIO;
            case DOCUMENT, PDF, TEXT -> FOLDER_FILES;
            default -> FOLDER_FILES;
        };
    }

    // ============================================================
    // KEY GENERATION
    // ============================================================

    private String generateKey(String folder, String filename) {
        String sanitized = sanitizeFilename(filename);
        return baseFolder + "/" + folder + "/" + UUID.randomUUID() + "-" + sanitized;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}