package ak.dev.khi_backend.khi_app.dto.media;

import lombok.*;

import java.util.List;

/**
 * MediaDtos — DTOs for the shared media upload endpoint used by every
 * Tiptap editor on the platform (News, Projects, About, Services, ...).
 *
 * The frontend calls POST /api/v1/media/upload to push a single image / audio /
 * video file to S3, then bakes the returned {@code fileUrl} into the editor HTML.
 */
public class MediaDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UploadResponse {
        private String fileUrl;
        private String fileName;
        private Long   fileSize;
        private String contentType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UploadEnvelope<T> {
        private boolean success;
        private String  message;
        private T       data;

        public static <T> UploadEnvelope<T> ok(T data, String message) {
            return UploadEnvelope.<T>builder()
                    .success(true)
                    .message(message)
                    .data(data)
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkUploadResponse {
        private List<UploadResponse> files;
    }
}
