package ak.dev.khi_backend.khi_app.dto.publishment.image;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageAlbumItemDTO {

    private Long id;              // response only
    private String imageUrl;      // response only (set after S3 upload)
    private String descriptionCkb;  // optional
    private String descriptionKmr;  // optional
    private Integer sortOrder;
}
