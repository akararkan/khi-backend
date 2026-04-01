package ak.dev.khi_backend.khi_app.enums.publishment;

/**
 * AttachmentType — the kind of supplementary file attached to a MULTI SoundTrack.
 *
 * Used in SoundTrackAttachment.attachmentType.
 */
public enum AttachmentType {

    /** PDF booklet, lyric sheet, liner notes … */
    PDF,

    /** Promotional or documentary video. */
    VIDEO,

    /** Standalone image (poster, artwork …). */
    IMAGE,

    /** Extra audio clip (intro, interview …). */
    AUDIO,

    /** Any other file type. */
    OTHER
}
