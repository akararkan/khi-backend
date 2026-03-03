package ak.dev.khi_backend.khi_app.model.publishment.video;

/**
 * VideoType — Defines the sub-type of a Video publishment.
 *
 *  FILM
 *    A traditional film or documentary.
 *    Has a single video source (URL / embed / external link).
 *    isAlbumOfMemories is always false.
 *
 *  VIDEO_CLIP
 *    A set / collection of short video clips.
 *    Each individual clip is a VideoClipItem child record.
 *    isAlbumOfMemories may be true (memorial clip set) or false.
 */
public enum VideoType {
    FILM,
    VIDEO_CLIP
}
