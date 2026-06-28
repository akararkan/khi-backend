package ak.dev.khi_backend.khi_app.enums;

import ak.dev.khi_backend.khi_app.enums.publishment.BookGenre;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookGenreTests {

    @Test
    void legacyAndUnknownGenresNormalizeToPublicContractValues() {
        assertThat(BookGenre.from("political")).isEqualTo(BookGenre.POLITICS);
        assertThat(BookGenre.from("academic")).isEqualTo(BookGenre.EDUCATIONAL);
        assertThat(BookGenre.from("essay")).isEqualTo(BookGenre.OTHER);
        assertThat(BookGenre.from("future_backend_value")).isEqualTo(BookGenre.OTHER);
        assertThat(BookGenre.POLITICAL.toJson()).isEqualTo("POLITICS");
    }
}
