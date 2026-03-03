package ak.dev.khi_backend.khi_app.enums.publishment;

/**
 * Defines the type of image collection:
 * - SINGLE: A single image with details
 * - GALLERY: Multiple images (photo album/gallery)
 * - PHOTO_STORY: Multiple images representing sequential steps of a process
 */
public enum ImageCollectionType {
    SINGLE,      // One image with details
    GALLERY,     // Multiple images (album/gallery)
    PHOTO_STORY  // Sequential step-by-step images (e.g., making a chair)
}