package ak.dev.khi_backend.khi_app.api.site;

import ak.dev.khi_backend.khi_app.dto.ApiResponse;
import ak.dev.khi_backend.khi_app.dto.site.SiteContentDtos.FeaturedResponse;
import ak.dev.khi_backend.khi_app.service.site.SiteContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Compatibility route used by the current public-site client.
 */
@RestController
@RequiredArgsConstructor
public class LegacyFeaturedController {
    private final SiteContentService siteContentService;

    @GetMapping("/featured")
    public ApiResponse<List<FeaturedResponse>> getFeatured(
            @RequestParam(required = false) String locale) {
        return ApiResponse.success(siteContentService.getFeatured(locale), "Featured items fetched");
    }
}
