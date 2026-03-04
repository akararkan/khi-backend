package ak.dev.khi_backend.khi_app.dto.about;


import lombok.*;
import java.util.List;

public class AboutDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AboutResponse {
        private Long id;
        private String slug;
        private String title;
        private String subtitle;
        private String metaDescription;
        private boolean active;
        private List<AboutBlockResponse> blocks;
        private String createdAt;
        private String updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AboutBlockResponse {
        private Long id;
        private String contentType;
        private Integer sequence;
        private String contentText;
        private String mediaUrl;
        private String thumbnailUrl;
        private String title;
        private String altText;
        private Object metadata;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AboutRequest {
        private String slug;
        private String title;
        private String subtitle;
        private String metaDescription;
        private List<AboutBlockRequest> blocks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AboutBlockRequest {
        private String contentType; // TEXT, IMAGE, VIDEO, AUDIO, GALLERY
        private Integer sequence;
        private String contentText;
        private String mediaUrl; // For existing media
        private String title;
        private String altText;
        private Object metadata;
    }

    @Data
    @Builder
    public static class UploadResponse {
        private String fileUrl;
        private String thumbnailUrl;
        private String fileName;
        private Long fileSize;
        private String contentType;
    }
}
