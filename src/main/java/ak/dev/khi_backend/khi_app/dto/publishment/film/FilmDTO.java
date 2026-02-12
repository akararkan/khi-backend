package ak.dev.khi_backend.khi_app.dto.publishment.film;

import ak.dev.khi_backend.khi_app.enums.Language;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilmDTO {

    private Long id;

    private FilmContentDTO ckbContent;
    private FilmContentDTO kmrContent;

    private String filmType;
    private String fileFormat;
    private Integer durationSeconds;
    private LocalDate publishmentDate;

    private String resolution;
    private Double fileSizeMb;

    // cover (upload OR direct url)
    private String coverUrl;

    // ✅ film source: upload/direct OR external OR embed
    private String sourceUrl;
    private String sourceExternalUrl;
    private String sourceEmbedUrl;

    private String durationFormatted;

    private Set<Language> contentLanguages;

    private Set<String> tagsCkb;
    private Set<String> tagsKmr;
    private Set<String> keywordsCkb;
    private Set<String> keywordsKmr;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
