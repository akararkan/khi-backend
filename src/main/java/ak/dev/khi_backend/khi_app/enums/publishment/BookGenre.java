package ak.dev.khi_backend.khi_app.enums.publishment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Book genre / category — replaces the old WritingTopic enum.
 *
 * ─── DB Migration ─────────────────────────────────────────────────────────────
 *
 *   -- Rename column (writing_topic → book_genre)
 *   ALTER TABLE writings RENAME COLUMN writing_topic TO book_genre;
 *
 *   -- Drop old index, create new one
 *   DROP INDEX IF EXISTS idx_writing_topic;
 *   CREATE INDEX idx_writing_genre ON writings (book_genre);
 *
 * ─── Old enum name: WritingTopic — update all DTO / service / controller
 *     references from writingTopic / getWritingTopic() → bookGenre / getBookGenre()
 */
public enum BookGenre {

    // ─── Literature & Creative Writing ────────────────────────────────────────
    POETRY,          // شیعر         — Poetry collections
    NOVEL,           // ڕۆمان        — Novels & long-form fiction
    SHORT_STORY,     // چیرۆکی کورت  — Short stories / novellas
    DRAMA,           // شانۆ         — Plays & dramatic works

    // ─── Humanities ────────────────────────────────────────────────────────────
    HISTORY,         // مێژوو        — Historical works
    BIOGRAPHY,       // ژیاننامە     — Biographies & memoirs
    PHILOSOPHY,      // فەلسەفە      — Philosophy
    RELIGION,        // ئایین        — Religious & theological texts
    FOLKLORE,        // زارگوتن      — Folklore, oral tradition, mythology

    // ─── Social & Political Sciences ──────────────────────────────────────────
    POLITICS,        // سیاسەت       — Political science & theory
    SOCIOLOGY,       // کۆمەڵناسی    — Sociology & social studies
    ECONOMICS,       // ئابووری      — Economics & finance
    LAW,             // یاسا         — Law & legal studies

    // ─── Language & Arts ──────────────────────────────────────────────────────
    LINGUISTICS,     // زمانناسی     — Linguistics & language studies
    ARTS,            // هونەر        — Visual arts, music, crafts
    CULTURAL,        // کولتووری     — Cultural studies & heritage

    // ─── Science & Applied Fields ─────────────────────────────────────────────
    SCIENCE,         // زانست        — Natural & applied sciences
    MEDICINE,        // پزیشکی       — Medical & health sciences
    EDUCATIONAL,     // پەروەردەیی   — Textbooks & academic works

    // ─── Special Categories ───────────────────────────────────────────────────
    CHILDREN,        // منداڵان      — Children's books
    TRAVEL,          // گەشتوگوزار   — Travel & geography
    OTHER;           // یتر          — Uncategorised / other

    @JsonCreator
    public static BookGenre from(String value) {
        if (value == null) return null;
        return BookGenre.valueOf(value.trim().toUpperCase());
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}