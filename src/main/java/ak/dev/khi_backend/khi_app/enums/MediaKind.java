package ak.dev.khi_backend.khi_app.enums;

/**
 * MediaKind — discriminator for an uploaded asset that backs a hero / cover
 * field or a gallery item on the modules that still maintain a standalone
 * media URL (News and Project).
 *
 *   IMAGE → rendered with <img>
 *   VIDEO → rendered with <video> (thumbnailUrl used as poster)
 *   AUDIO → rendered with <audio> (thumbnailUrl used as cover art)
 *
 * About, Contact, and Service no longer use this enum — they store all
 * media (image / video / voice / document / other) inside the bilingual
 * Tiptap description HTML, where the
 * {@link ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor}
 * is the single point of media handling.
 */
public enum MediaKind {
    IMAGE,
    VIDEO,
    AUDIO
}
