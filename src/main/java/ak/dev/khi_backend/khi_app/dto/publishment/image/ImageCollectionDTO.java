package ak.dev.khi_backend.khi_app.dto.publishment.image;


import ak.dev.khi_backend.khi_app.enums.Language;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageCollectionDTO {

    // ─── Response only ────────────────────────────────────────────────
    private Long id;

    // ─── Bilingual content ────────────────────────────────────────────
    private ImageContentDTO ckbContent;
    private ImageContentDTO kmrContent;

    // ─── S3 URLs (set by service after upload) ────────────────────────
    private String coverUrl;

    // ─── Image album items (response: includes imageUrl from S3) ──────
    private List<ImageAlbumItemDTO> imageAlbum;

    // ─── Metadata ─────────────────────────────────────────────────────
    private LocalDate publishmentDate;

    // ─── Languages ────────────────────────────────────────────────────
    private Set<Language> contentLanguages;

    // ─── Tags & Keywords (bilingual) ──────────────────────────────────
    private Set<String> tagsCkb;
    private Set<String> tagsKmr;
    private Set<String> keywordsCkb;
    private Set<String> keywordsKmr;

    // ─── Response only — timestamps ───────────────────────────────────
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
