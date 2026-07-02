package ak.dev.khi_backend.khi_app.service.media;

import ak.dev.khi_backend.khi_app.dto.media.MediaDtos.UploadResponse;
import ak.dev.khi_backend.khi_app.enums.project.ProjectMediaType;
import ak.dev.khi_backend.khi_app.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MediaService — shared S3 upload service used by every Tiptap editor across
 * the platform. Mirrors the logic that previously lived in AboutService, but
 * exposed at a neutral endpoint so News / Projects / Services / Sound / Video
 * / Image / Writing can all call it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final S3Service s3Service;

    /**
     * Upload a single file to S3 and return a public URL.
     *
     * @param file the file uploaded as multipart/form-data
     * @param type optional folder hint: "image", "audio", "video", "document",
     *             or "gallery" (treated as image). When null, the folder is
     *             inferred from the content type.
     */
    public UploadResponse upload(MultipartFile file, String type) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        log.info("Uploading media: name={}, hint={}, contentType={}, size={}",
                file.getOriginalFilename(), type,
                file.getContentType(), file.getSize());

        ProjectMediaType mediaType = resolveMediaType(type);

        String fileUrl = mediaType != null
                ? s3Service.upload(file::getInputStream, file.getSize(),
                file.getOriginalFilename(), file.getContentType(), mediaType)
                : s3Service.upload(file::getInputStream, file.getSize(),
                file.getOriginalFilename(), file.getContentType());

        log.info("Upload successful: {}", fileUrl);

        return UploadResponse.builder()
                .fileUrl(fileUrl)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .build();
    }

    public List<UploadResponse> uploadMultiple(List<MultipartFile> files, String type) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .map(file -> {
                    try {
                        return upload(file, type);
                    } catch (IOException e) {
                        log.error("Failed to upload: {}", file.getOriginalFilename(), e);
                        throw new RuntimeException("Upload failed: " + file.getOriginalFilename(), e);
                    }
                })
                .collect(Collectors.toList());
    }

    public void delete(String fileUrl) {
        if (fileUrl != null && !fileUrl.isBlank()) {
            s3Service.deleteFile(fileUrl);
            log.info("Deleted media from S3: {}", fileUrl);
        }
    }

    private ProjectMediaType resolveMediaType(String hint) {
        if (hint == null || hint.isBlank()) return null;
        return switch (hint.toLowerCase().trim()) {
            case "image", "gallery" -> ProjectMediaType.IMAGE;
            case "video"            -> ProjectMediaType.VIDEO;
            case "audio"            -> ProjectMediaType.AUDIO;
            case "document", "pdf"  -> ProjectMediaType.DOCUMENT;
            default                 -> null;
        };
    }
}
