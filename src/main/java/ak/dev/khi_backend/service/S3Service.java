package ak.dev.khi_backend.service;

import ak.dev.khi_backend.enums.project.ProjectMediaType;
import ak.dev.khi_backend.exceptions.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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

    /**
     * Upload file with automatic folder detection based on content type
     */
    public String upload(byte[] fileBytes, String originalFilename, String contentType) {

        if (fileBytes == null || fileBytes.length == 0) {
            throw new BadRequestException("media.invalid", "File is empty or null");
        }

        String folder = detectFolder(contentType);
        String key = generateKey(folder, originalFilename);

        log.info("Uploading to S3: bucket={}, folder={}, key={}, contentType={}",
                bucket, folder, key, contentType);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(fileBytes));

        String publicUrl = getPublicUrl(key);
        log.info("File uploaded successfully: {}", publicUrl);

        return publicUrl;
    }

    /**
     * Upload file with explicit media type (use this when you know the ProjectMediaType)
     */
    public String upload(byte[] fileBytes, String originalFilename, String contentType, ProjectMediaType mediaType) {

        if (fileBytes == null || fileBytes.length == 0) {
            throw new BadRequestException("media.invalid", "File is empty or null");
        }

        String folder = getFolderForMediaType(mediaType);
        String key = generateKey(folder, originalFilename);

        log.info("Uploading to S3: bucket={}, folder={}, mediaType={}, key={}",
                bucket, folder, mediaType, key);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(fileBytes));

        String publicUrl = getPublicUrl(key);
        log.info("File uploaded successfully: {}", publicUrl);

        return publicUrl;
    }

    // ============================================================
    // FOLDER DETECTION
    // ============================================================

    /**
     * Detect folder based on content type (MIME type)
     */
    private String detectFolder(String contentType) {
        if (contentType == null) {
            return FOLDER_FILES;
        }

        String type = contentType.toLowerCase();

        if (type.startsWith("image/")) {
            return FOLDER_IMAGES;
        }
        if (type.startsWith("video/")) {
            return FOLDER_VIDEOS;
        }
        if (type.startsWith("audio/")) {
            return FOLDER_AUDIO;
        }

        // Default to files for documents, PDFs, etc.
        return FOLDER_FILES;
    }

    /**
     * Get folder based on ProjectMediaType enum
     */
    private String getFolderForMediaType(ProjectMediaType mediaType) {
        if (mediaType == null) {
            return FOLDER_FILES;
        }

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
        // Structure: testweb/images/uuid-filename.jpg
        return baseFolder + "/" + folder + "/" + UUID.randomUUID() + "-" + sanitized;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "file";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String getPublicUrl(String key) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }
}