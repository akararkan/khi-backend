package ak.dev.khi_backend.khi_app.enums;

/**
 * MediaKind — discriminator for an uploaded asset that backs a hero / cover
 * field or a gallery item across About, Contact, News, and Project entities.
 *
 *   IMAGE → rendered with <img>
 *   VIDEO → rendered with <video> (thumbnailUrl used as poster)
 *   AUDIO → rendered with <audio> (thumbnailUrl used as cover art)
 *
 * Mirrors {@link ak.dev.khi_backend.khi_app.model.service.ServiceMediaCollection.MediaType}
 * but lives at a neutral package so it can be shared by the JSONB-backed
 * lightweight galleries on the About / Contact / News / Project entities.
 */
public enum MediaKind {
    IMAGE,
    VIDEO,
    AUDIO
}
