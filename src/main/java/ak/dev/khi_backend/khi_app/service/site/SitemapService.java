package ak.dev.khi_backend.khi_app.service.site;

import ak.dev.khi_backend.khi_app.dto.site.SiteContentDtos.SitemapResponse;
import ak.dev.khi_backend.khi_app.model.about.About;
import ak.dev.khi_backend.khi_app.model.contact.Contact;
import ak.dev.khi_backend.khi_app.repository.about.AboutRepository;
import ak.dev.khi_backend.khi_app.repository.contact.ContactRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsRepository;
import ak.dev.khi_backend.khi_app.repository.project.ProjectRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.image.ImageCollectionRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.sound.SoundTrackRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.video.VideoRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.writing.WritingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SitemapService {

    private final AboutRepository aboutRepository;
    private final ContactRepository contactRepository;
    private final NewsRepository newsRepository;
    private final ProjectRepository projectRepository;
    private final WritingRepository writingRepository;
    private final SoundTrackRepository soundTrackRepository;
    private final VideoRepository videoRepository;
    private final ImageCollectionRepository imageCollectionRepository;

    @Transactional(readOnly = true)
    public SitemapResponse generate(String requestedLocale) {
        String locale = normalizeLocale(requestedLocale);
        boolean kmr = locale.equals("ku");
        List<String> paths = new ArrayList<>(List.of(
                "/" + locale,
                "/" + locale + "/about",
                "/" + locale + "/contact",
                "/" + locale + "/services",
                "/" + locale + "/donate",
                "/" + locale + "/news",
                "/" + locale + "/projects",
                "/" + locale + "/writings",
                "/" + locale + "/audio",
                "/" + locale + "/videos",
                "/" + locale + "/gallery"
        ));

        aboutRepository.findAll().stream().filter(About::isActive)
                .map(page -> kmr && page.getSlugKmr() != null ? page.getSlugKmr() : page.getSlugCkb())
                .filter(slug -> slug != null && !slug.isBlank())
                .map(slug -> "/" + locale + "/about/" + slug)
                .forEach(paths::add);

        contactRepository.findAll().stream().filter(Contact::isActive)
                .map(page -> kmr && page.getSlugKmr() != null ? page.getSlugKmr() : page.getSlugCkb())
                .filter(slug -> slug != null && !slug.isBlank())
                .map(slug -> "/" + locale + "/contact/" + slug)
                .forEach(paths::add);

        newsRepository.findAll().forEach(item -> paths.add("/" + locale + "/news/" + item.getId()));
        projectRepository.findAll().forEach(item -> paths.add("/" + locale + "/projects/" + item.getId()));
        writingRepository.findAll().forEach(item -> paths.add("/" + locale + "/writings/" + item.getId()));
        soundTrackRepository.findAll().forEach(item -> paths.add("/" + locale + "/audio/" + item.getId()));
        videoRepository.findAll().forEach(item -> paths.add("/" + locale + "/videos/" + item.getId()));
        imageCollectionRepository.findAll()
                .forEach(item -> paths.add("/" + locale + "/gallery/" + item.getId()));

        return SitemapResponse.builder().locale(locale).paths(paths.stream().distinct().toList()).build();
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) return "ckb";
        String value = locale.trim().toLowerCase(Locale.ROOT);
        return value.equals("kmr") || value.equals("ku") ? "ku" : "ckb";
    }
}
