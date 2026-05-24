package ak.dev.khi_backend.khi_app.seed;

import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.enums.MediaKind;
import ak.dev.khi_backend.khi_app.enums.project.ProjectStatus;
import ak.dev.khi_backend.khi_app.enums.publishment.*;
import ak.dev.khi_backend.khi_app.model.about.About;
import ak.dev.khi_backend.khi_app.model.about.AboutContent;
import ak.dev.khi_backend.khi_app.model.about.StatItem;
import ak.dev.khi_backend.khi_app.model.contact.Contact;
import ak.dev.khi_backend.khi_app.model.contact.ContactContent;
import ak.dev.khi_backend.khi_app.model.media.MediaItem;
import ak.dev.khi_backend.khi_app.model.news.News;
import ak.dev.khi_backend.khi_app.model.news.NewsCategory;
import ak.dev.khi_backend.khi_app.model.news.NewsContent;
import ak.dev.khi_backend.khi_app.model.news.NewsSubCategory;
import ak.dev.khi_backend.khi_app.model.project.Project;
import ak.dev.khi_backend.khi_app.model.project.ProjectContentBlock;
import ak.dev.khi_backend.khi_app.model.project.ProjectKeyword;
import ak.dev.khi_backend.khi_app.model.project.ProjectTag;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageAlbumItem;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageCollection;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageContent;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrack;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrackAttachment;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrackBrochure;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrackContent;
import ak.dev.khi_backend.khi_app.model.publishment.sound.SoundTrackFile;
import ak.dev.khi_backend.khi_app.model.publishment.topic.PublishmentTopic;
import ak.dev.khi_backend.khi_app.model.publishment.video.Video;
import ak.dev.khi_backend.khi_app.model.publishment.video.VideoClipItem;
import ak.dev.khi_backend.khi_app.model.publishment.video.VideoContent;
import ak.dev.khi_backend.khi_app.model.publishment.video.VideoType;
import ak.dev.khi_backend.khi_app.model.publishment.writing.Writing;
import ak.dev.khi_backend.khi_app.model.publishment.writing.WritingContent;
import ak.dev.khi_backend.khi_app.model.service.ServiceContent;
import ak.dev.khi_backend.khi_app.repository.about.AboutRepository;
import ak.dev.khi_backend.khi_app.repository.contact.ContactRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsCategoryRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsRepository;
import ak.dev.khi_backend.khi_app.repository.news.NewsSubCategoryRepository;
import ak.dev.khi_backend.khi_app.repository.project.ProjectKeywordRepository;
import ak.dev.khi_backend.khi_app.repository.project.ProjectRepository;
import ak.dev.khi_backend.khi_app.repository.project.ProjectTagRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.image.ImageCollectionRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.sound.SoundTrackRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.topic.PublishmentTopicRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.video.VideoRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.writing.WritingRepository;
import ak.dev.khi_backend.khi_app.repository.service.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * MockDataSeeder — populates every public-facing module with bilingual
 * (Sorani / Kurmanji) mock data and real public-internet media URLs so the
 * full stack can be exercised end-to-end without a manual data-entry pass.
 *
 * <p><b>Activation.</b>  Disabled by default.  Enable by setting either
 * the JVM/env property <code>app.seed.enabled=true</code> or by passing
 * <code>--app.seed.enabled=true</code> to the Spring Boot launcher.</p>
 *
 * <p><b>Idempotency.</b>  Each per-entity seed method first calls
 * <code>repository.count()</code>.  When the table already has rows, the
 * seed step is skipped — so it is safe to leave enabled across restarts.</p>
 *
 * <p><b>Resilience.</b>  Each section is wrapped in its own try/catch so
 * that a problem in one module does not block the rest.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@Order(100)
public class MockDataSeeder implements CommandLineRunner {

    // ─── Repositories ────────────────────────────────────────────────────────
    private final AboutRepository                aboutRepository;
    private final ContactRepository              contactRepository;
    private final ServiceRepository              serviceRepository;
    private final NewsRepository                 newsRepository;
    private final NewsCategoryRepository         newsCategoryRepository;
    private final NewsSubCategoryRepository      newsSubCategoryRepository;
    private final ProjectRepository              projectRepository;
    private final ProjectTagRepository           projectTagRepository;
    private final ProjectKeywordRepository       projectKeywordRepository;
    private final SoundTrackRepository           soundTrackRepository;
    private final VideoRepository                videoRepository;
    private final ImageCollectionRepository      imageCollectionRepository;
    private final WritingRepository              writingRepository;
    private final PublishmentTopicRepository     publishmentTopicRepository;

    // ─── Reusable media URL bank ─────────────────────────────────────────────

    /** Unsplash photo URLs — stable, free for editorial use. */
    private static final String IMG_LIBRARY        = "https://images.unsplash.com/photo-1507842217343-583bb7270b66?w=1600";
    private static final String IMG_BOOKS_SHELF    = "https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=1600";
    private static final String IMG_STAGE          = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1600";
    private static final String IMG_KURDISH_FLAG   = "https://images.unsplash.com/photo-1583952676057-79b6a04f3fed?w=1600";
    private static final String IMG_ERBIL_CITADEL  = "https://images.unsplash.com/photo-1599584831060-6f0e9c7e1b73?w=1600";
    private static final String IMG_ARCHIVE        = "https://images.unsplash.com/photo-1568667256549-094345857637?w=1600";
    private static final String IMG_OPEN_BOOK      = "https://images.unsplash.com/photo-1532012197267-da84d127e765?w=1600";
    private static final String IMG_OLD_DOCUMENT   = "https://images.unsplash.com/photo-1606326608606-aa0b62935f2b?w=1600";
    private static final String IMG_TRADITIONAL    = "https://images.unsplash.com/photo-1542816417-0983c9c9ad53?w=1600";
    private static final String IMG_MOUNTAINS_KRD  = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=1600";
    private static final String IMG_INTERVIEW      = "https://images.unsplash.com/photo-1573497019940-1c28c88b4f3e?w=1600";
    private static final String IMG_AUDIO_STUDIO   = "https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?w=1600";
    private static final String IMG_PRESENTER      = "https://images.unsplash.com/photo-1485827404703-89b55fcc595e?w=1600";
    private static final String IMG_CONCERT        = "https://images.unsplash.com/photo-1429962714451-bb934ecdc4ec?w=1600";
    private static final String IMG_FOLK_DANCE     = "https://images.unsplash.com/photo-1503095396549-807759245b35?w=1600";
    private static final String IMG_CALLIGRAPHY    = "https://images.unsplash.com/photo-1455390582262-044cdead277a?w=1600";
    private static final String IMG_NEWSPAPER      = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=1600";
    private static final String IMG_TEAM           = "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=1600";
    private static final String IMG_BUILDING       = "https://images.unsplash.com/photo-1486325212027-8081e485255e?w=1600";
    private static final String IMG_LOBBY          = "https://images.unsplash.com/photo-1497366216548-37526070297c?w=1600";

    /** Public-domain sample MP4s from Google's storage bucket. */
    private static final String VID_BIG_BUCK_BUNNY = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
    private static final String VID_FOR_BIGGER     = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4";
    private static final String VID_ELEPHANTS      = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4";
    private static final String VID_SINTEL         = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4";
    private static final String VID_TEARS          = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4";

    /** Royalty-free MP3 demos from SoundHelix. */
    private static final String AUD_SONG_1 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3";
    private static final String AUD_SONG_2 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3";
    private static final String AUD_SONG_3 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3";
    private static final String AUD_SONG_4 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3";
    private static final String AUD_SONG_5 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3";

    /** Stable sample PDFs / documents. */
    private static final String PDF_DUMMY      = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf";
    private static final String PDF_BROCHURE   = "https://file-examples.com/storage/fe1c9483b7669a6da27cb15/2017/10/file-sample_150kB.pdf";

    // =========================================================================
    // ENTRY POINT
    // =========================================================================

    @Override
    public void run(String... args) {
        log.info("================================================================");
        log.info(" MockDataSeeder starting — app.seed.enabled=true");
        log.info("================================================================");

        safe("About",            this::seedAbout);
        safe("Contact",          this::seedContact);
        safe("Service",          this::seedServices);
        safe("News",             this::seedNews);
        safe("Project",          this::seedProjects);
        safe("PublishmentTopic", this::seedTopics);
        safe("SoundTrack",       this::seedSoundTracks);
        safe("Video",            this::seedVideos);
        safe("ImageCollection",  this::seedImageCollections);
        safe("Writing",          this::seedWritings);

        log.info("================================================================");
        log.info(" MockDataSeeder finished");
        log.info("================================================================");
    }

    private void safe(String name, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.error("[seed:{}] FAILED — {}", name, e.getMessage(), e);
        }
    }

    // =========================================================================
    // 1) ABOUT
    // =========================================================================

    @Transactional
    public void seedAbout() {
        if (aboutRepository.count() > 0) {
            log.info("[seed:About] table not empty — skipping");
            return;
        }

        About a1 = About.builder()
                .slugCkb("دەربارەی-ئێمە")
                .slugKmr("derbare-me")
                .active(true)
                .displayOrder(0)
                .ckbContent(AboutContent.builder()
                        .title("دەربارەی دامەزراوەی کوردستان بۆ هونەر و مێژوو")
                        .subtitle("لۆتکەی شارستانیەتی کوردی لە یەک شوێن")
                        .metaDescription("دامەزراوەی کوردستان بۆ هونەر و مێژوو ، ناوەندێکی کولتووری و ئەکادیمی سەربەخۆیە کە لە ساڵی ٢٠١٠ دامەزراوە و کاردەکات لەسەر پاراستن و باڵوکردنەوەی میراتی نووسراو و دەنگی و وێنەیی گەلی کورد.")
                        .body("""
                            <h1>دەربارەی ئێمە</h1>
                            <p>دامەزراوەی کوردستان بۆ هونەر و مێژوو، ناوەندێکی کولتووری و ئەکادیمی سەربەخۆیە کە لە ساڵی ٢٠١٠ لە شاری هەولێر دامەزراوە. ئەرکی سەرەکیمان پاراستنە لە میراتی نووسراو و دەنگی و وێنەیی گەلی کورد بە هەردوو زاراوەی سۆرانی و کرمانجی.</p>
                            <img src=\"%s\" alt=\"کتێبخانە\">
                            <h2>ئامانجەکانمان</h2>
                            <ul>
                                <li>کۆکردنەوەی نووسراوە میژووییەکان</li>
                                <li>تۆمارکردنی دەنگی هونەرمەندە کۆنەکان</li>
                                <li>وەشاندنی توێژینەوەی زانستی</li>
                                <li>پشتیوانی لە نووسەرانی نوێ</li>
                            </ul>
                            <video controls src=\"%s\"></video>
                            <h2>تیمی کارا</h2>
                            <p>زیاتر لە ٢٠ توێژەر و ئەکادیمی لە بوارەکانی مێژوو ، هونەر ، زمانەوانی و ئەدەب کار دەکەن.</p>
                            <img src=\"%s\" alt=\"تیمی کار\">
                            <audio controls src=\"%s\"></audio>
                            <p><a href=\"%s\">پەڕەی ناساندنی کامڵ (PDF)</a></p>
                            """.formatted(IMG_LIBRARY, VID_BIG_BUCK_BUNNY, IMG_TEAM, AUD_SONG_1, PDF_DUMMY))
                        .build())
                .kmrContent(AboutContent.builder()
                        .title("Derbarê Saziya Kurdistanê ji bo Huner û Dîrokê")
                        .subtitle("Lûtkeya şaristaniya kurdî di yek cihî de")
                        .metaDescription("Saziya Kurdistanê ji bo Huner û Dîrokê, navendeke çandî û akademîk a serbixwe ye ku di sala 2010ê de hatiye damezrandin û li ser parastina mîrateya nivîskî, dengî û dîtbarî ya gelê kurd dixebite.")
                        .body("""
                            <h1>Derbarê me</h1>
                            <p>Saziya Kurdistanê ji bo Huner û Dîrokê, navendeke çandî û akademîk a serbixwe ye ku di sala 2010ê de li bajarê Hewlêrê hatiye damezrandin. Erkê me yê sereke parastina mîrateya nivîskî, dengî û dîtbarî ya gelê kurd e bi her du zaravayên soranî û kurmancî.</p>
                            <img src=\"%s\" alt=\"Pirtûkxane\">
                            <h2>Armancên me</h2>
                            <ul>
                                <li>Berhevkirina nivîsên dîrokî</li>
                                <li>Tomarkirina dengên hunermendên kevn</li>
                                <li>Weşandina lêkolînên zanistî</li>
                                <li>Piştgiriya nivîskarên nû</li>
                            </ul>
                            <video controls src=\"%s\"></video>
                            <h2>Tîma me</h2>
                            <p>Zêdetir ji 20 lêkolîner û akademîsyen di waran de dîrok, huner, zimanzanî û edebiyatê dixebitin.</p>
                            <img src=\"%s\" alt=\"Tîm\">
                            <audio controls src=\"%s\"></audio>
                            <p><a href=\"%s\">Pelê pêşkêşê yê tam (PDF)</a></p>
                            """.formatted(IMG_LIBRARY, VID_BIG_BUCK_BUNNY, IMG_TEAM, AUD_SONG_1, PDF_DUMMY))
                        .build())
                .stats(List.of(
                        StatItem.builder().labelCkb("کتێب").labelKmr("Pirtûk").value("5,000+").build(),
                        StatItem.builder().labelCkb("توێژینەوە").labelKmr("Lêkolîn").value("1,200+").build(),
                        StatItem.builder().labelCkb("هونەرمەند").labelKmr("Hunermend").value("350+").build(),
                        StatItem.builder().labelCkb("ساڵ کارکردن").labelKmr("Sal xebat").value("15+").build()
                ))
                .build();

        About a2 = About.builder()
                .slugCkb("مێژووی-دامەزراوە")
                .slugKmr("diroka-saziye")
                .active(true)
                .displayOrder(1)
                .ckbContent(AboutContent.builder()
                        .title("مێژووی دامەزراوەکەمان")
                        .subtitle("لە ٢٠١٠ەوە تا ئەمڕۆ")
                        .metaDescription("لە ساڵی ٢٠١٠ەوە ، دامەزراوەکەمان بەشێوەی بەردەوام لە کار کردن لەسەر پاراستنی شوناسی کوردی بووە.")
                        .body("""
                            <h2>سەرەتای دامەزراوە</h2>
                            <p>لە ساڵی ٢٠١٠، گرووپێک لە توێژەران و ئەکادیمیە کوردەکان بڕیاریاندا دامەزراوەیەکی سەربەخۆ دامەزرێنن کە یاریدەدەری پاراستنی کولتوری کوردی بێت.</p>
                            <img src=\"%s\" alt=\"کۆن\">
                            <h2>گرنگترین ڕووداوەکان</h2>
                            <p>لە ساڵی ٢٠١٥ ، یەکەم کۆنفرانسی نێودەوڵەتی هونەری کوردی بەڕێوەبرا.</p>
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_OLD_DOCUMENT, VID_FOR_BIGGER))
                        .build())
                .kmrContent(AboutContent.builder()
                        .title("Dîroka saziya me")
                        .subtitle("Ji 2010ê heta îro")
                        .metaDescription("Ji sala 2010ê ve, saziya me bi awayekî berdewam li ser parastina nasnameya kurdî dixebite.")
                        .body("""
                            <h2>Destpêka saziyê</h2>
                            <p>Di sala 2010ê de, komek lêkolîner û akademîsyenên kurd biryar dan ku saziyeke serbixwe damezrînin ku alîkariya parastina çanda kurdî bike.</p>
                            <img src=\"%s\" alt=\"Kevn\">
                            <h2>Bûyerên herî girîng</h2>
                            <p>Di sala 2015an de, konferansa yekem a navneteweyî ya hunera kurdî hat li dar xistin.</p>
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_OLD_DOCUMENT, VID_FOR_BIGGER))
                        .build())
                .stats(List.of(
                        StatItem.builder().labelCkb("ساڵ").labelKmr("Sal").value("15").build(),
                        StatItem.builder().labelCkb("کۆنفرانس").labelKmr("Konferans").value("42").build()
                ))
                .build();

        About a3 = About.builder()
                .slugCkb("بینشمان-و-ئامانج")
                .slugKmr("dîtin-armanc")
                .active(true)
                .displayOrder(2)
                .ckbContent(AboutContent.builder()
                        .title("بینش و ئامانجی ستراتیجیمان")
                        .subtitle("بەرەو داهاتوویەکی ڕووناک بۆ کولتووری کوردی")
                        .metaDescription("ستراتیژیی ئێمە لەسەر سێ ستوون ڕاوەستاوە: پاراستن، باڵوکردنەوە، و گەشەسەندن.")
                        .body("""
                            <h1>بینشمان</h1>
                            <p>بەردەستکردنی هەموو میراتی نووسراو و دەنگی و وێنەیی کوردی بە جیهان بەشێوەیەکی دیجیتاڵی و ئاسان.</p>
                            <img src=\"%s\" alt=\"ئامانج\">
                            <h2>ستوونەکانی کارمان</h2>
                            <ol>
                                <li><strong>پاراستن:</strong> دیجیتاڵکردنی دۆکیومێنتە کۆنەکان</li>
                                <li><strong>باڵوکردنەوە:</strong> ئامادەکردنی ناوەرۆکی نوێ</li>
                                <li><strong>گەشە:</strong> پشتیوانی لە نەوەی نوێ</li>
                            </ol>
                            <audio controls src=\"%s\"></audio>
                            """.formatted(IMG_ARCHIVE, AUD_SONG_2))
                        .build())
                .kmrContent(AboutContent.builder()
                        .title("Dîtin û armanca me ya stratejîk")
                        .subtitle("Berbi pêşerojeke ronî ji bo çanda kurdî")
                        .metaDescription("Stratejiya me li ser sê stûnan rawestiyaye: parastin, belavkirin, û pêşveçûn.")
                        .body("""
                            <h1>Dîtina me</h1>
                            <p>Berdestkirina hemû mîrateya nivîskî, dengî û dîtbarî ya kurdî bi cîhanê re bi awayekî dîjîtal û hêsan.</p>
                            <img src=\"%s\" alt=\"Armanc\">
                            <h2>Stûnên xebata me</h2>
                            <ol>
                                <li><strong>Parastin:</strong> Dîjîtalkirina belgenameyên kevn</li>
                                <li><strong>Belavkirin:</strong> Amadekirina naveroka nû</li>
                                <li><strong>Pêşveçûn:</strong> Piştgiriya nifşê nû</li>
                            </ol>
                            <audio controls src=\"%s\"></audio>
                            """.formatted(IMG_ARCHIVE, AUD_SONG_2))
                        .build())
                .stats(List.of(
                        StatItem.builder().labelCkb("ستراتیژی").labelKmr("Stratejî").value("3").build()
                ))
                .build();

        About a4 = About.builder()
                .slugCkb("تیمەکەمان")
                .slugKmr("tima-me")
                .active(true)
                .displayOrder(3)
                .ckbContent(AboutContent.builder()
                        .title("تیمی توێژەران و کارمەندان")
                        .subtitle("کەسانێک کە بە دڵ کاردەکەن بۆ کولتووری کوردی")
                        .metaDescription("تیمی ئێمە پێکهاتووە لە توێژەر و ئەکادیمی و کارمەندانی پسپۆڕ لە بوارەکانی مێژوو، زمان، هونەر و تەکنەلۆژیا.")
                        .body("""
                            <h1>تیمەکەمان</h1>
                            <p>سەرکەوتنی هەر دامەزراوەیەک بەستراوەتەوە بە کوالێتی تیمەکەی. تیمی ئێمە پێکهاتووە لە زیاتر لە ٢٠ توێژەر و ئەکادیمی و کارمەندی پسپۆڕ.</p>
                            <img src=\"%s\" alt=\"تیم\">
                            <h2>بەشەکانی کار</h2>
                            <ul>
                                <li>بەشی توێژینەوەی مێژوویی</li>
                                <li>بەشی زمانەوانی و وەرگێڕان</li>
                                <li>بەشی ئەرشیف و دیجیتاڵکردن</li>
                                <li>بەشی تەکنەلۆژیا و تۆڕ</li>
                            </ul>
                            <video controls src=\"%s\"></video>
                            <p>هەموو ئەندامانی تیم خاوەنی پسپۆڕیی بەرزن لە بوارەکانیان.</p>
                            """.formatted(IMG_TEAM, VID_SINTEL))
                        .build())
                .kmrContent(AboutContent.builder()
                        .title("Tîma lêkolîner û karmendan")
                        .subtitle("Kesên ku bi dil ji bo çanda kurdî dixebitin")
                        .metaDescription("Tîma me ji lêkolîner, akademîsyen û karmendên pispor ên warên dîrok, ziman, huner û teknolojiyê pêk tê.")
                        .body("""
                            <h1>Tîma me</h1>
                            <p>Serkeftina her saziyê bi kalîteya tîma wê ve girêdayî ye. Tîma me ji zêdetirî 20 lêkolîner, akademîsyen û karmendên pispor pêk tê.</p>
                            <img src=\"%s\" alt=\"Tîm\">
                            <h2>Beşên xebatê</h2>
                            <ul>
                                <li>Beşa lêkolîna dîrokî</li>
                                <li>Beşa zimanzanî û wergerê</li>
                                <li>Beşa arşîv û dîjîtalkirinê</li>
                                <li>Beşa teknolojî û torê</li>
                            </ul>
                            <video controls src=\"%s\"></video>
                            <p>Hemû endamên tîmê xwedî pisporiya bilind in di warên xwe de.</p>
                            """.formatted(IMG_TEAM, VID_SINTEL))
                        .build())
                .stats(List.of(
                        StatItem.builder().labelCkb("ئەندامی تیم").labelKmr("Endamê tîmê").value("20+").build(),
                        StatItem.builder().labelCkb("بەش").labelKmr("Beş").value("4").build(),
                        StatItem.builder().labelCkb("پسپۆڕ").labelKmr("Pispor").value("12").build()
                ))
                .build();

        About a5 = About.builder()
                .slugCkb("بەهاکانمان")
                .slugKmr("nirxen-me")
                .active(true)
                .displayOrder(4)
                .ckbContent(AboutContent.builder()
                        .title("بەها بنەڕەتییەکانمان")
                        .subtitle("ئەو بنەماانەی کارمان ڕابەرایەتی دەکەن")
                        .metaDescription("بەهاکانی ئێمە: سەربەخۆیی، ڕاستگۆیی زانستی، کراوەیی، و ڕێزگرتن لە جیاوازی کولتووری.")
                        .body("""
                            <h1>بەهاکانمان</h1>
                            <p>هەموو کارێکی ئێمە لەسەر کۆمەڵێک بەهای بنەڕەتی بنیات نراوە کە سیمای دامەزراوەکەمان دیاری دەکەن.</p>
                            <img src=\"%s\" alt=\"بەها\">
                            <h2>بنەماکانمان</h2>
                            <ol>
                                <li><strong>سەربەخۆیی:</strong> هیچ لایەنگیریەکی سیاسی یان ئاینی نییە</li>
                                <li><strong>ڕاستگۆیی زانستی:</strong> پشتبەستن بە سەرچاوەی متمانەپێکراو</li>
                                <li><strong>کراوەیی:</strong> بەردەستکردنی زانیاری بۆ هەمووان</li>
                                <li><strong>ڕێز:</strong> ڕێزگرتن لە هەموو زاراوە و ناوچەکانی کوردستان</li>
                            </ol>
                            <audio controls src=\"%s\"></audio>
                            """.formatted(IMG_OPEN_BOOK, AUD_SONG_3))
                        .build())
                .kmrContent(AboutContent.builder()
                        .title("Nirxên me yên bingehîn")
                        .subtitle("Ew bingeh ku xebata me birêve dibin")
                        .metaDescription("Nirxên me: serbixwebûn, rastiya zanistî, vekirîbûn, û rêzgirtina ji cudahiya çandî.")
                        .body("""
                            <h1>Nirxên me</h1>
                            <p>Hemû xebata me li ser komek nirxên bingehîn hatiye avakirin ku sîmaya saziya me diyar dikin.</p>
                            <img src=\"%s\" alt=\"Nirx\">
                            <h2>Bingehên me</h2>
                            <ol>
                                <li><strong>Serbixwebûn:</strong> Tu alîgiriya siyasî an dînî tune</li>
                                <li><strong>Rastiya zanistî:</strong> Piştgirî bi çavkaniyên pêbawer</li>
                                <li><strong>Vekirîbûn:</strong> Berdestkirina zanyariyê ji bo herkesî</li>
                                <li><strong>Rêz:</strong> Rêzgirtin ji hemû zarava û herêmên Kurdistanê</li>
                            </ol>
                            <audio controls src=\"%s\"></audio>
                            """.formatted(IMG_OPEN_BOOK, AUD_SONG_3))
                        .build())
                .stats(List.of(
                        StatItem.builder().labelCkb("بەهای بنەڕەتی").labelKmr("Nirxê bingehîn").value("4").build()
                ))
                .build();

        About a6 = About.builder()
                .slugCkb("هاوکاری-و-پەیوەندی")
                .slugKmr("hevkari-girêdan")
                .active(true)
                .displayOrder(5)
                .ckbContent(AboutContent.builder()
                        .title("هاوکارەکان و پەیوەندییە نێودەوڵەتییەکان")
                        .subtitle("کارکردن لەگەڵ دامەزراوەکانی جیهان")
                        .metaDescription("دامەزراوەکەمان هاوکاری دەکات لەگەڵ زانکۆ و دامەزراوە کولتوورییەکانی ناوخۆ و دەرەوە.")
                        .body("""
                            <h1>هاوکارەکانمان</h1>
                            <p>بۆ گەیشتن بە ئامانجەکانمان، هاوکاری دەکەین لەگەڵ کۆمەڵێک زانکۆ و دامەزراوەی کولتووری لە ناوخۆ و نێودەوڵەتی.</p>
                            <img src=\"%s\" alt=\"هاوکاری\">
                            <h2>جۆرەکانی هاوکاری</h2>
                            <ul>
                                <li>هاوبەشی توێژینەوە لەگەڵ زانکۆکان</li>
                                <li>ئاڵوگۆڕی ئەرشیف و زانیاری</li>
                                <li>کۆنفرانس و وۆرکشۆپی هاوبەش</li>
                                <li>پرۆژەی دیجیتاڵکردنی هاوبەش</li>
                            </ul>
                            <video controls src=\"%s\"></video>
                            <p><a href=\"%s\">لیستی هاوکارەکان (PDF)</a></p>
                            """.formatted(IMG_BUILDING, VID_FOR_BIGGER, PDF_DUMMY))
                        .build())
                .kmrContent(AboutContent.builder()
                        .title("Hevkar û girêdanên navneteweyî")
                        .subtitle("Xebat bi saziyên cîhanê re")
                        .metaDescription("Saziya me bi zanîngeh û saziyên çandî yên hundir û derve re hevkariyê dike.")
                        .body("""
                            <h1>Hevkarên me</h1>
                            <p>Ji bo gihîştina armancên me, em bi komek zanîngeh û saziyên çandî yên hundir û navneteweyî re hevkariyê dikin.</p>
                            <img src=\"%s\" alt=\"Hevkarî\">
                            <h2>Cûreyên hevkariyê</h2>
                            <ul>
                                <li>Hevparî di lêkolînê de bi zanîngehan re</li>
                                <li>Guhertina arşîv û zanyariyê</li>
                                <li>Konferans û atolyeyên hevpar</li>
                                <li>Projeyên dîjîtalkirinê yên hevpar</li>
                            </ul>
                            <video controls src=\"%s\"></video>
                            <p><a href=\"%s\">Lîsteya hevkaran (PDF)</a></p>
                            """.formatted(IMG_BUILDING, VID_FOR_BIGGER, PDF_DUMMY))
                        .build())
                .stats(List.of(
                        StatItem.builder().labelCkb("هاوکار").labelKmr("Hevkar").value("18").build(),
                        StatItem.builder().labelCkb("وڵات").labelKmr("Welat").value("9").build()
                ))
                .build();

        About a7 = About.builder()
                .slugCkb("ئەرشیف-و-کۆگا")
                .slugKmr("arşîv-kogeh")
                .active(true)
                .displayOrder(6)
                .ckbContent(AboutContent.builder()
                        .title("ئەرشیف و کۆگای دیجیتاڵیمان")
                        .subtitle("گەورەترین کۆگای میراتی کوردی")
                        .metaDescription("ئەرشیفی دیجیتاڵی ئێمە زیاتر لە ١٠،٠٠٠ دۆکیومێنت و ٥،٠٠٠ کتێب و هەزاران تۆماری دەنگی لەخۆ دەگرێت.")
                        .body("""
                            <h1>ئەرشیفەکەمان</h1>
                            <p>دامەزراوەکەمان خاوەنی یەکێکە لە گەورەترین ئەرشیفە دیجیتاڵییەکانی میراتی کوردی لە جیهان.</p>
                            <img src=\"%s\" alt=\"ئەرشیف\">
                            <h2>ناوەرۆکی ئەرشیف</h2>
                            <ul>
                                <li>دۆکیومێنتە مێژووییە کۆنەکان</li>
                                <li>کتێب و دەستنووسە کلاسیکیەکان</li>
                                <li>تۆماری دەنگی هونەرمەندان</li>
                                <li>وێنە و فیلمە مێژووییەکان</li>
                            </ul>
                            <video controls src=\"%s\"></video>
                            <audio controls src=\"%s\"></audio>
                            <p><a href=\"%s\">ڕێبەری ئەرشیف (PDF)</a></p>
                            """.formatted(IMG_ARCHIVE, VID_ELEPHANTS, AUD_SONG_4, PDF_DUMMY))
                        .build())
                .kmrContent(AboutContent.builder()
                        .title("Arşîv û kogeha me ya dîjîtal")
                        .subtitle("Mezintirîn kogeha mîrateya kurdî")
                        .metaDescription("Arşîva me ya dîjîtal zêdetirî 10,000 belgename, 5,000 pirtûk û bi hezaran tomarên dengî dihewîne.")
                        .body("""
                            <h1>Arşîva me</h1>
                            <p>Saziya me xwediyê yek ji mezintirîn arşîvên dîjîtal ên mîrateya kurdî ye li cîhanê.</p>
                            <img src=\"%s\" alt=\"Arşîv\">
                            <h2>Naveroka arşîvê</h2>
                            <ul>
                                <li>Belgenameyên dîrokî yên kevn</li>
                                <li>Pirtûk û destnivîsên klasîk</li>
                                <li>Tomarên dengî yên hunermendan</li>
                                <li>Wêne û fîlmên dîrokî</li>
                            </ul>
                            <video controls src=\"%s\"></video>
                            <audio controls src=\"%s\"></audio>
                            <p><a href=\"%s\">Rêbera arşîvê (PDF)</a></p>
                            """.formatted(IMG_ARCHIVE, VID_ELEPHANTS, AUD_SONG_4, PDF_DUMMY))
                        .build())
                .stats(List.of(
                        StatItem.builder().labelCkb("دۆکیومێنت").labelKmr("Belgename").value("10,000+").build(),
                        StatItem.builder().labelCkb("کتێب").labelKmr("Pirtûk").value("5,000+").build(),
                        StatItem.builder().labelCkb("تۆماری دەنگی").labelKmr("Tomarê dengî").value("3,500+").build()
                ))
                .build();

        About a8 = About.builder()
                .slugCkb("پشتگیری-و-بەخشین")
                .slugKmr("piştgirî-bexşîn")
                .active(true)
                .displayOrder(7)
                .ckbContent(AboutContent.builder()
                        .title("پشتگیری بکە لە ئەرکەکەمان")
                        .subtitle("بەشداربە لە پاراستنی میراتی کوردی")
                        .metaDescription("دەتوانیت پشتگیری دامەزراوەکەمان بکەیت بە ڕێگەی بەخشین، خۆبەخشی، یان بەخشینی دۆکیومێنت و کتێب.")
                        .body("""
                            <h1>پشتگیریمان بکە</h1>
                            <p>وەک دامەزراوەیەکی سەربەخۆ، پشتگیری تۆ یارمەتیمان دەدات بۆ بەردەوامبوون لە ئەرکی پاراستنی میراتی کوردی.</p>
                            <img src=\"%s\" alt=\"پشتگیری\">
                            <h2>چۆن پشتگیری بکەیت؟</h2>
                            <ol>
                                <li><strong>بەخشینی دارایی:</strong> یارمەتیدانی پرۆژەکانمان</li>
                                <li><strong>خۆبەخشی:</strong> بەشداری بە کات و بەهرەکانت</li>
                                <li><strong>بەخشینی میرات:</strong> پێشکەشکردنی دۆکیومێنت و کتێب</li>
                            </ol>
                            <video controls src=\"%s\"></video>
                            <p><a href=\"%s\">فۆرمی بەخشین (PDF)</a></p>
                            """.formatted(IMG_LIBRARY, VID_FOR_BIGGER, PDF_DUMMY))
                        .build())
                .kmrContent(AboutContent.builder()
                        .title("Piştgiriya erkê me bike")
                        .subtitle("Beşdarî parastina mîrateya kurdî bibe")
                        .metaDescription("Tu dikarî piştgiriya saziya me bikî bi rêya bexşîn, dilxwazî, an bexşîna belgename û pirtûkan.")
                        .body("""
                            <h1>Piştgiriya me bike</h1>
                            <p>Wek saziyeke serbixwe, piştgiriya te alîkariya me dike ku berdewam bibin di erkê parastina mîrateya kurdî de.</p>
                            <img src=\"%s\" alt=\"Piştgirî\">
                            <h2>Çawa piştgirî bikî?</h2>
                            <ol>
                                <li><strong>Bexşîna darayî:</strong> Alîkariya projeyên me</li>
                                <li><strong>Dilxwazî:</strong> Beşdarî bi dem û jêhatîbûna te</li>
                                <li><strong>Bexşîna mîrateyê:</strong> Pêşkêşkirina belgename û pirtûkan</li>
                            </ol>
                            <video controls src=\"%s\"></video>
                            <p><a href=\"%s\">Forma bexşînê (PDF)</a></p>
                            """.formatted(IMG_LIBRARY, VID_FOR_BIGGER, PDF_DUMMY))
                        .build())
                .stats(List.of(
                        StatItem.builder().labelCkb("بەخشەر").labelKmr("Bexşkar").value("250+").build(),
                        StatItem.builder().labelCkb("خۆبەخش").labelKmr("Dilxwaz").value("80+").build()
                ))
                .build();

        aboutRepository.saveAll(List.of(a1, a2, a3, a4, a5, a6, a7, a8));
        log.info("[seed:About] inserted 8 rows");
    }

    // =========================================================================
    // 2) CONTACT
    // =========================================================================

    @Transactional
    public void seedContact() {
        if (contactRepository.count() > 0) {
            log.info("[seed:Contact] table not empty — skipping");
            return;
        }

        Contact c1 = Contact.builder()
                .slugCkb("پەیوەندی-هەولێر")
                .slugKmr("peywendi-hewler")
                .active(true)
                .displayOrder(0)
                .ckbContent(ContactContent.builder()
                        .title("پەیوەندیمان پێوەبکە - نووسینگەی هەولێر")
                        .subtitle("ئامادەین بۆ خزمەتکردنت")
                        .address("شەقامی ٦٠ مەتری، نزیک قەڵای هەولێر، هەولێر، هەرێمی کوردستان")
                        .workingHours("یەکشەممە - پێنجشەممە، ٩:٠٠ - ١٧:٠٠")
                        .description("""
                            <h2>سەردانیمان بکە</h2>
                            <p>نووسینگەکەمان لە ناوەندی شاری هەولێر هەڵکەوتووە و دەرگاکانمان کراوەن بۆ سەردانی توێژەران و هاووڵاتیان.</p>
                            <img src=\"%s\" alt=\"بینای نووسینگە\">
                            <h3>چۆن دێیتە لامان؟</h3>
                            <p>نزیک کۆیلایی قەڵای هەولێر ، ١٠ خولەک بە پێ لە بازاڕی قەیسەری.</p>
                            <video controls src=\"%s\"></video>
                            <audio controls src=\"%s\"></audio>
                            <p><a href=\"%s\">نەخشەی شوێن (PDF)</a></p>
                            """.formatted(IMG_LOBBY, VID_FOR_BIGGER, AUD_SONG_3, PDF_DUMMY))
                        .build())
                .kmrContent(ContactContent.builder()
                        .title("Bi me re têkilî daynin - Buroya Hewlêrê")
                        .subtitle("Em amade ne ji bo xizmeta we")
                        .address("Kolana 60 metroyî, nêzîkî Kelaya Hewlêrê, Hewlêr, Herêma Kurdistanê")
                        .workingHours("Yekşem - Pêncşem, 9:00 - 17:00")
                        .description("""
                            <h2>Serdana me bikin</h2>
                            <p>Buroya me li navenda bajarê Hewlêrê ye û deriyên me ji bo lêkolîner û welatiyan vekirî ne.</p>
                            <img src=\"%s\" alt=\"Avahiya buroyê\">
                            <h3>Çawa hûn werin cem me?</h3>
                            <p>Nêzîkî Kelaya Hewlêrê, 10 deqîqe bi peyatî ji Sûka Qeyseriyê.</p>
                            <video controls src=\"%s\"></video>
                            <audio controls src=\"%s\"></audio>
                            <p><a href=\"%s\">Nexşeya cî (PDF)</a></p>
                            """.formatted(IMG_LOBBY, VID_FOR_BIGGER, AUD_SONG_3, PDF_DUMMY))
                        .build())
                .phone("+964 750 123 4567")
                .secondaryPhone("+964 770 987 6543")
                .email("info@khi-institute.org")
                .mapEmbedUrl("https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d3221.123!2d44.0091!3d36.1911!2m3")
                .latitude(36.1911)
                .longitude(44.0091)
                .build();

        Contact c2 = Contact.builder()
                .slugCkb("پەیوەندی-سلێمانی")
                .slugKmr("peywendi-silemani")
                .active(true)
                .displayOrder(1)
                .ckbContent(ContactContent.builder()
                        .title("نووسینگەی سلێمانی")
                        .subtitle("لە دڵی شاری ڕووناکبیری کوردی")
                        .address("گەڕەکی سالم، ڕووبەڕووی پارکی ئازادی، سلێمانی")
                        .workingHours("یەکشەممە - پێنجشەممە، ١٠:٠٠ - ١٨:٠٠")
                        .description("""
                            <h2>نووسینگەی دووەم</h2>
                            <p>سلێمانی شاری ڕووناکبیری و هونەرە لە کوردستان، و نووسینگەی ئێمە لێرە کاردەکات لەسەر پرۆژە ئەکادیمیەکان.</p>
                            <img src=\"%s\" alt=\"سلێمانی\">
                            <audio controls src=\"%s\"></audio>
                            """.formatted(IMG_BUILDING, AUD_SONG_4))
                        .build())
                .kmrContent(ContactContent.builder()
                        .title("Buroya Silêmaniyê")
                        .subtitle("Di dilê bajarê ronakbîriya kurdî de")
                        .address("Taxa Salim, beramberî Parka Azadiyê, Silêmanî")
                        .workingHours("Yekşem - Pêncşem, 10:00 - 18:00")
                        .description("""
                            <h2>Buroya duyem</h2>
                            <p>Silêmanî bajarê ronakbîrî û hunerê ye li Kurdistanê, û buroya me li vir li ser projeyên akademîk dixebite.</p>
                            <img src=\"%s\" alt=\"Silêmanî\">
                            <audio controls src=\"%s\"></audio>
                            """.formatted(IMG_BUILDING, AUD_SONG_4))
                        .build())
                .phone("+964 770 222 3333")
                .email("slemani@khi-institute.org")
                .mapEmbedUrl("https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d3220.456!2d45.4344!3d35.5567!2m3")
                .latitude(35.5567)
                .longitude(45.4344)
                .build();

        Contact c3 = Contact.builder()
                .slugCkb("پەیوەندی-دهۆک")
                .slugKmr("peywendi-duhok")
                .active(true)
                .displayOrder(2)
                .ckbContent(ContactContent.builder()
                        .title("نووسینگەی دهۆک - ناوەندی کرمانجی")
                        .subtitle("خزمەتگوزاری لە بەهدینان")
                        .address("شەقامی نەورۆز، دهۆک، هەرێمی کوردستان")
                        .workingHours("شەممە - چوارشەممە، ٩:٠٠ - ١٦:٠٠")
                        .description("""
                            <h2>ناوەندی کرمانجی</h2>
                            <p>ئەم نووسینگەیە تایبەتە بە خزمەتگوزاری بەشی کرمانجی و چاودێریکردنی پرۆژەکانی پاراستنی زمانی کرمانجی.</p>
                            <img src=\"%s\" alt=\"دهۆک\">
                            """.formatted(IMG_MOUNTAINS_KRD))
                        .build())
                .kmrContent(ContactContent.builder()
                        .title("Buroya Duhokê - Navenda Kurmancî")
                        .subtitle("Xizmetkirin li Behdînan")
                        .address("Kolana Newroz, Duhok, Herêma Kurdistanê")
                        .workingHours("Şemî - Çarşem, 9:00 - 16:00")
                        .description("""
                            <h2>Navenda kurmancî</h2>
                            <p>Ev buro taybet e ji bo xizmetkirina beşa kurmancî û çavdêrîkirina projeyên parastina zimanê kurmancî.</p>
                            <img src=\"%s\" alt=\"Duhok\">
                            """.formatted(IMG_MOUNTAINS_KRD))
                        .build())
                .phone("+964 750 444 5555")
                .email("duhok@khi-institute.org")
                .latitude(36.8669)
                .longitude(42.9886)
                .build();

        Contact c4 = Contact.builder()
                .slugCkb("پەیوەندی-گشتی")
                .slugKmr("peywendi-giştî")
                .active(true)
                .displayOrder(3)
                .ckbContent(ContactContent.builder()
                        .title("پەیوەندی گشتی و داواکاریی زانیاری")
                        .subtitle("هەر پرسیارێکت هەیە، ئێمە لێرەین")
                        .address("ناوەندی سەرەکی، شەقامی ٦٠ مەتری، هەولێر")
                        .workingHours("یەکشەممە - پێنجشەممە، ٨:٣٠ - ١٦:٣٠")
                        .description("""
                            <h2>پەیوەندیی گشتی</h2>
                            <p>بۆ هەر پرسیار یان داواکاریەکی گشتی دەربارەی خزمەتگوزاری و پرۆژەکانمان، دەتوانیت پەیوەندیمان پێوەبکەیت.</p>
                            <img src=\"%s\" alt=\"پەیوەندی\">
                            <h3>چۆن یارمەتیت بدەین؟</h3>
                            <ul>
                                <li>زانیاری دەربارەی خزمەتگوزاریەکان</li>
                                <li>داواکاریی هاوکاری و هاوبەشی</li>
                                <li>پرسیاری دەربارەی ئەرشیف</li>
                            </ul>
                            <p><a href=\"%s\">فۆرمی پەیوەندی (PDF)</a></p>
                            """.formatted(IMG_LOBBY, PDF_DUMMY))
                        .build())
                .kmrContent(ContactContent.builder()
                        .title("Têkiliya giştî û daxwaza zanyariyê")
                        .subtitle("Her pirsa we hebe, em li vir in")
                        .address("Navenda sereke, Kolana 60 metroyî, Hewlêr")
                        .workingHours("Yekşem - Pêncşem, 8:30 - 16:30")
                        .description("""
                            <h2>Têkiliya giştî</h2>
                            <p>Ji bo her pirs an daxwazeke giştî li ser xizmet û projeyên me, hûn dikarin bi me re têkilî daynin.</p>
                            <img src=\"%s\" alt=\"Têkilî\">
                            <h3>Çawa alîkariya we bikin?</h3>
                            <ul>
                                <li>Zanyarî li ser xizmetan</li>
                                <li>Daxwaza hevkarî û hevpariyê</li>
                                <li>Pirs li ser arşîvê</li>
                            </ul>
                            <p><a href=\"%s\">Forma têkiliyê (PDF)</a></p>
                            """.formatted(IMG_LOBBY, PDF_DUMMY))
                        .build())
                .phone("+964 750 111 2222")
                .secondaryPhone("+964 770 333 4444")
                .email("contact@khi-institute.org")
                .build();

        Contact c5 = Contact.builder()
                .slugCkb("پەیوەندی-میدیا")
                .slugKmr("peywendi-medya")
                .active(true)
                .displayOrder(4)
                .ckbContent(ContactContent.builder()
                        .title("پەیوەندیی میدیا و ڕۆژنامەوانی")
                        .subtitle("بۆ ڕۆژنامەنووسان و میدیاکان")
                        .address("بەشی پەیوەندییەکان، ناوەندی سەرەکی، هەولێر")
                        .workingHours("یەکشەممە - پێنجشەممە، ٩:٠٠ - ١٧:٠٠")
                        .description("""
                            <h2>بەشی میدیا</h2>
                            <p>ئەم بەشە تایبەتە بۆ ڕۆژنامەنووسان و میدیاکان کە دەیانەوێت زانیاری یان چاوپێکەوتن لەگەڵ دامەزراوەکەمان.</p>
                            <img src=\"%s\" alt=\"میدیا\">
                            <video controls src=\"%s\"></video>
                            <p>بۆ داواکردنی چاوپێکەوتن، تکایە لە ڕێگەی ئیمەیڵەوە پەیوەندیمان پێوەبکە.</p>
                            """.formatted(IMG_PRESENTER, VID_FOR_BIGGER))
                        .build())
                .kmrContent(ContactContent.builder()
                        .title("Têkiliya medya û rojnamegeriyê")
                        .subtitle("Ji bo rojnamevan û medyayan")
                        .address("Beşa têkiliyan, Navenda sereke, Hewlêr")
                        .workingHours("Yekşem - Pêncşem, 9:00 - 17:00")
                        .description("""
                            <h2>Beşa medyayê</h2>
                            <p>Ev beş taybet e ji bo rojnamevan û medyayan ku dixwazin zanyarî an hevpeyvînê bi saziya me re bikin.</p>
                            <img src=\"%s\" alt=\"Medya\">
                            <video controls src=\"%s\"></video>
                            <p>Ji bo daxwaza hevpeyvînê, ji kerema xwe bi rêya e-nameyê bi me re têkilî daynin.</p>
                            """.formatted(IMG_PRESENTER, VID_FOR_BIGGER))
                        .build())
                .phone("+964 751 555 6666")
                .email("media@khi-institute.org")
                .build();

        Contact c6 = Contact.builder()
                .slugCkb("پەیوەندی-هەلی-کار")
                .slugKmr("peywendi-kar")
                .active(true)
                .displayOrder(5)
                .ckbContent(ContactContent.builder()
                        .title("هەلی کار و بەشداری")
                        .subtitle("بەشێک بە لە تیمەکەمان")
                        .address("بەشی سەرچاوە مرۆییەکان، هەولێر")
                        .workingHours("یەکشەممە - پێنجشەممە، ٩:٠٠ - ١٦:٠٠")
                        .description("""
                            <h2>هەلی کار</h2>
                            <p>ئەگەر بە دڵتە کار بکەیت لە بواری پاراستنی کولتووری کوردی، ئێمە بەردەوام بەدوای کەسانی بەهرەمەنددا دەگەڕێین.</p>
                            <img src=\"%s\" alt=\"کار\">
                            <h3>بوارەکانی پێویستی</h3>
                            <ul>
                                <li>توێژەری مێژوو و زمان</li>
                                <li>پسپۆڕی ئەرشیف و دیجیتاڵکردن</li>
                                <li>پەرەپێدەری وێب و تەکنەلۆژیا</li>
                                <li>بەڕێوەبەری پرۆژە</li>
                            </ul>
                            <p><a href=\"%s\">فۆرمی داواکاری کار (PDF)</a></p>
                            """.formatted(IMG_TEAM, PDF_DUMMY))
                        .build())
                .kmrContent(ContactContent.builder()
                        .title("Derfeta kar û beşdarî")
                        .subtitle("Bibe beşek ji tîma me")
                        .address("Beşa çavkaniyên mirovî, Hewlêr")
                        .workingHours("Yekşem - Pêncşem, 9:00 - 16:00")
                        .description("""
                            <h2>Derfeta kar</h2>
                            <p>Heke bi dilê te ye ku di warê parastina çanda kurdî de bixebitî, em berdewam li kesên jêhatî digerin.</p>
                            <img src=\"%s\" alt=\"Kar\">
                            <h3>Warên pêwîst</h3>
                            <ul>
                                <li>Lêkolînerê dîrok û ziman</li>
                                <li>Pisporê arşîv û dîjîtalkirinê</li>
                                <li>Pêşvebirê malper û teknolojiyê</li>
                                <li>Birêvebirê projeyê</li>
                            </ul>
                            <p><a href=\"%s\">Forma daxwaza karî (PDF)</a></p>
                            """.formatted(IMG_TEAM, PDF_DUMMY))
                        .build())
                .phone("+964 750 777 8888")
                .email("careers@khi-institute.org")
                .build();

        Contact c7 = Contact.builder()
                .slugCkb("پەیوەندی-توێژینەوە")
                .slugKmr("peywendi-lêkolîn")
                .active(true)
                .displayOrder(6)
                .ckbContent(ContactContent.builder()
                        .title("بەشی توێژینەوە و هاوکاریی ئەکادیمی")
                        .subtitle("بۆ توێژەران و زانکۆکان")
                        .address("بەشی توێژینەوە، ناوەندی سەرەکی، هەولێر")
                        .workingHours("یەکشەممە - پێنجشەممە، ٩:٠٠ - ١٧:٠٠")
                        .description("""
                            <h2>بەشی توێژینەوە</h2>
                            <p>ئەم بەشە خزمەتگوزاری دەکات بۆ توێژەران و زانکۆکانی کە دەیانەوێت دەستیان بگات بە ئەرشیف و سەرچاوەکانمان.</p>
                            <img src=\"%s\" alt=\"توێژینەوە\">
                            <h3>خزمەتگوزارییەکان</h3>
                            <ul>
                                <li>دەستگەیشتن بە ئەرشیفی دیجیتاڵی</li>
                                <li>هاوکاری لە پرۆژەی توێژینەوە</li>
                                <li>داواکردنی کۆپی دۆکیومێنت</li>
                            </ul>
                            <p><a href=\"%s\">فۆرمی داواکاری توێژینەوە (PDF)</a></p>
                            """.formatted(IMG_OPEN_BOOK, PDF_DUMMY))
                        .build())
                .kmrContent(ContactContent.builder()
                        .title("Beşa lêkolîn û hevkariya akademîk")
                        .subtitle("Ji bo lêkolîner û zanîngehan")
                        .address("Beşa lêkolînê, Navenda sereke, Hewlêr")
                        .workingHours("Yekşem - Pêncşem, 9:00 - 17:00")
                        .description("""
                            <h2>Beşa lêkolînê</h2>
                            <p>Ev beş xizmetê dike ji bo lêkolîner û zanîngehan ku dixwazin bigihîjin arşîv û çavkaniyên me.</p>
                            <img src=\"%s\" alt=\"Lêkolîn\">
                            <h3>Xizmet</h3>
                            <ul>
                                <li>Gihîştina arşîva dîjîtal</li>
                                <li>Hevkarî di projeyên lêkolînê de</li>
                                <li>Daxwaza kopiya belgename</li>
                            </ul>
                            <p><a href=\"%s\">Forma daxwaza lêkolînê (PDF)</a></p>
                            """.formatted(IMG_OPEN_BOOK, PDF_DUMMY))
                        .build())
                .phone("+964 750 999 0000")
                .email("research@khi-institute.org")
                .build();

        Contact c8 = Contact.builder()
                .slugCkb("پەیوەندی-کۆمەڵایەتی")
                .slugKmr("peywendi-civakî")
                .active(true)
                .displayOrder(7)
                .ckbContent(ContactContent.builder()
                        .title("تۆڕە کۆمەڵایەتییەکان و گەیاندن")
                        .subtitle("شوێنمان بکەوە لە تۆڕە کۆمەڵایەتییەکان")
                        .address("بەشی گەیاندن، ناوەندی سەرەکی، هەولێر")
                        .workingHours("هەموو ڕۆژان، ٢٤ کاتژمێر ئۆنلاین")
                        .description("""
                            <h2>تۆڕە کۆمەڵایەتییەکان</h2>
                            <p>ئێمە چالاکین لەسەر زۆربەی پلاتفۆرمە کۆمەڵایەتییەکان بۆ گەیاندنی نوێترین چالاکی و ناوەرۆکمان.</p>
                            <img src=\"%s\" alt=\"کۆمەڵایەتی\">
                            <h3>پەیوەندیمان پێوەبکە لە</h3>
                            <ul>
                                <li>فەیسبووک، ئینستاگرام، یوتیوب</li>
                                <li>ئیکس (تویتەری پێشوو)</li>
                                <li>تێلیگرام و واتساپ</li>
                            </ul>
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_PRESENTER, VID_SINTEL))
                        .build())
                .kmrContent(ContactContent.builder()
                        .title("Torên civakî û ragihandin")
                        .subtitle("Li ser torên civakî me bişopîne")
                        .address("Beşa ragihandinê, Navenda sereke, Hewlêr")
                        .workingHours("Hemû roj, 24 saet onlîne")
                        .description("""
                            <h2>Torên civakî</h2>
                            <p>Em çalak in li ser piraniya platformên civakî ji bo ragihandina nûtirîn çalakî û naveroka me.</p>
                            <img src=\"%s\" alt=\"Civakî\">
                            <h3>Bi me re têkilî daynin li</h3>
                            <ul>
                                <li>Facebook, Instagram, YouTube</li>
                                <li>X (Twitter berê)</li>
                                <li>Telegram û WhatsApp</li>
                            </ul>
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_PRESENTER, VID_SINTEL))
                        .build())
                .phone("+964 751 222 1111")
                .email("social@khi-institute.org")
                .build();

        contactRepository.saveAll(List.of(c1, c2, c3, c4, c5, c6, c7, c8));
        log.info("[seed:Contact] inserted 8 rows");
    }

    // =========================================================================
    // 3) SERVICE
    // =========================================================================

    @Transactional
    public void seedServices() {
        if (serviceRepository.count() > 0) {
            log.info("[seed:Service] table not empty — skipping");
            return;
        }

        ak.dev.khi_backend.khi_app.model.service.Service s1 =
                ak.dev.khi_backend.khi_app.model.service.Service.builder()
                        .serviceType("Training")
                        .location("Erbil, KHI Hall")
                        .active(true)
                        .publishedAt(LocalDateTime.now().minusDays(30))
                        .build();
        s1.addContent(ServiceContent.builder()
                .languageCode("CKB")
                .title("خولی فێرکاری نووسینی ئەکادیمی")
                .description("""
                    <h2>دەربارەی خولەکە</h2>
                    <p>ئەم خولە بۆ نووسەرانی نوێی کورد ئامادە کراوە کە دەیانەوێت پێشکەوتنی نووسینی ئەکادیمی فێر ببن. خولەکە بەردەوام دەبێت بۆ ٣ مانگ.</p>
                    <img src=\"%s\" alt=\"خولی نووسین\">
                    <h3>بەرنامەی خول</h3>
                    <ul>
                        <li>هەفتەی یەکەم: بنەماکانی نووسین</li>
                        <li>هەفتەی دووەم: لێکۆڵینەوەی زانستی</li>
                        <li>هەفتەی سێیەم: ڕێبازەکانی توێژینەوە</li>
                    </ul>
                    <video controls src=\"%s\"></video>
                    <p><a href=\"%s\">پرۆگرامی تەواو (PDF)</a></p>
                    """.formatted(IMG_OPEN_BOOK, VID_FOR_BIGGER, PDF_DUMMY))
                .build());
        s1.addContent(ServiceContent.builder()
                .languageCode("KMR")
                .title("Kursa fêrkirina nivîsîna akademîk")
                .description("""
                    <h2>Derbarê kursê</h2>
                    <p>Ev kurs ji bo nivîskarên kurd ên nû hatiye amadekirin ku dixwazin pêşveçûnê di nivîsîna akademîk de fêr bibin. Kurs 3 mehan berdewam dike.</p>
                    <img src=\"%s\" alt=\"Kursa nivîsê\">
                    <h3>Bername</h3>
                    <ul>
                        <li>Hefteya yekem: Bingehên nivîsê</li>
                        <li>Hefteya duyem: Lêkolîna zanistî</li>
                        <li>Hefteya sêyem: Rêbazên lêkolînê</li>
                    </ul>
                    <video controls src=\"%s\"></video>
                    <p><a href=\"%s\">Bernameya tam (PDF)</a></p>
                    """.formatted(IMG_OPEN_BOOK, VID_FOR_BIGGER, PDF_DUMMY))
                .build());

        ak.dev.khi_backend.khi_app.model.service.Service s2 =
                ak.dev.khi_backend.khi_app.model.service.Service.builder()
                        .serviceType("Event")
                        .location("Sulaymaniyah, Cultural Center")
                        .active(true)
                        .publishedAt(LocalDateTime.now().minusDays(10))
                        .build();
        s2.addContent(ServiceContent.builder()
                .languageCode("CKB")
                .title("کۆنفرانسی نێودەوڵەتی مێژووی کورد")
                .description("""
                    <h2>کۆنفرانسی ساڵانە</h2>
                    <p>کۆنفرانسێکی نێودەوڵەتی کە سەرجەم لێکۆڵەران و توێژەرە کوردیەکانی جیهان کۆ دەکاتەوە بۆ پێشکەشکردنی توێژینەوەی نوێ لەسەر مێژووی کورد.</p>
                    <img src=\"%s\" alt=\"کۆنفرانس\">
                    <video controls src=\"%s\"></video>
                    <audio controls src=\"%s\"></audio>
                    """.formatted(IMG_STAGE, VID_ELEPHANTS, AUD_SONG_1))
                .build());
        s2.addContent(ServiceContent.builder()
                .languageCode("KMR")
                .title("Konferansa navneteweyî ya dîroka kurdî")
                .description("""
                    <h2>Konferansa salane</h2>
                    <p>Konferanseke navneteweyî ye ku hemû lêkolîner û lêkolînerên kurd ên cîhanê dicivîne ji bo pêşkêşkirina lêkolînên nû li ser dîroka kurdî.</p>
                    <img src=\"%s\" alt=\"Konferans\">
                    <video controls src=\"%s\"></video>
                    <audio controls src=\"%s\"></audio>
                    """.formatted(IMG_STAGE, VID_ELEPHANTS, AUD_SONG_1))
                .build());

        ak.dev.khi_backend.khi_app.model.service.Service s3 =
                ak.dev.khi_backend.khi_app.model.service.Service.builder()
                        .serviceType("Program")
                        .location("Online")
                        .active(true)
                        .publishedAt(LocalDateTime.now().minusDays(5))
                        .build();
        s3.addContent(ServiceContent.builder()
                .languageCode("CKB")
                .title("بەرنامەی پاراستنی فۆلکلۆری کوردی")
                .description("""
                    <p>بەرنامەیەکی ساڵانە کە مەبەستی پاراستن و باڵوکردنەوەی فۆلکلۆری کوردیە. لێدوانە بەشە:</p>
                    <ul>
                        <li>چیرۆکە کۆنەکان</li>
                        <li>گۆرانی فۆلکلۆری</li>
                        <li>دەستەواژە و دوواندنە کوردیەکان</li>
                    </ul>
                    <img src=\"%s\" alt=\"فۆلکلۆر\">
                    <audio controls src=\"%s\"></audio>
                    """.formatted(IMG_FOLK_DANCE, AUD_SONG_5))
                .build());
        s3.addContent(ServiceContent.builder()
                .languageCode("KMR")
                .title("Bernameya parastina folklora kurdî")
                .description("""
                    <p>Bernameyeke salane ku armanca wê parastin û belavkirina folklora kurdî ye. Beş wiha ne:</p>
                    <ul>
                        <li>Çîrokên kevn</li>
                        <li>Stranên folklorîk</li>
                        <li>Biwêj û peyvên kurdî</li>
                    </ul>
                    <img src=\"%s\" alt=\"Folklor\">
                    <audio controls src=\"%s\"></audio>
                    """.formatted(IMG_FOLK_DANCE, AUD_SONG_5))
                .build());

        ak.dev.khi_backend.khi_app.model.service.Service s4 =
                ak.dev.khi_backend.khi_app.model.service.Service.builder()
                        .serviceType("Workshop")
                        .location("Duhok University")
                        .active(true)
                        .publishedAt(LocalDateTime.now().minusDays(2))
                        .build();
        s4.addContent(ServiceContent.builder()
                .languageCode("CKB")
                .title("وۆرکشۆپی هونەری خوسنۆڤی کوردی")
                .description("<p>وۆرکشۆپێکی پراکتیکی بۆ فێربوونی هونەری خوسنۆڤی کوردی لە لایەن مامۆستا کاراکانەوە.</p><img src=\"%s\"><video controls src=\"%s\"></video>".formatted(IMG_CALLIGRAPHY, VID_SINTEL))
                .build());
        s4.addContent(ServiceContent.builder()
                .languageCode("KMR")
                .title("Atolyeya hunera xetatiya kurdî")
                .description("<p>Atolyeyeke praktîk ji bo fêrbûna hunera xetatiya kurdî ji aliyê mamosteyên jêhatî ve.</p><img src=\"%s\"><video controls src=\"%s\"></video>".formatted(IMG_CALLIGRAPHY, VID_SINTEL))
                .build());

        ak.dev.khi_backend.khi_app.model.service.Service s5 =
                ak.dev.khi_backend.khi_app.model.service.Service.builder()
                        .serviceType("Training")
                        .location("Erbil, KHI Hall")
                        .active(true)
                        .publishedAt(LocalDateTime.now().minusDays(20))
                        .build();
        s5.addContent(ServiceContent.builder()
                .languageCode("CKB")
                .title("خولی فێرکاری دیجیتاڵکردنی ئەرشیف")
                .description("""
                    <h2>دەربارەی خولەکە</h2>
                    <p>خولێکی پراکتیکی بۆ فێربوونی تەکنیکەکانی دیجیتاڵکردن و پاراستنی دۆکیومێنتە مێژووییەکان.</p>
                    <img src=\"%s\" alt=\"دیجیتاڵکردن\">
                    <h3>بابەتەکان</h3>
                    <ul>
                        <li>سکانکردن و کوالێتی وێنە</li>
                        <li>میتاداتا و پۆلێنکردن</li>
                        <li>پاراستنی فایلی دیجیتاڵی</li>
                    </ul>
                    <video controls src=\"%s\"></video>
                    """.formatted(IMG_ARCHIVE, VID_FOR_BIGGER))
                .build());
        s5.addContent(ServiceContent.builder()
                .languageCode("KMR")
                .title("Kursa fêrkirina dîjîtalkirina arşîvê")
                .description("""
                    <h2>Derbarê kursê</h2>
                    <p>Kurseke praktîk ji bo fêrbûna teknîkên dîjîtalkirin û parastina belgenameyên dîrokî.</p>
                    <img src=\"%s\" alt=\"Dîjîtalkirin\">
                    <h3>Mijar</h3>
                    <ul>
                        <li>Skankirin û kalîteya wêneyê</li>
                        <li>Metadata û rêzkirin</li>
                        <li>Parastina pelê dîjîtal</li>
                    </ul>
                    <video controls src=\"%s\"></video>
                    """.formatted(IMG_ARCHIVE, VID_FOR_BIGGER))
                .build());

        ak.dev.khi_backend.khi_app.model.service.Service s6 =
                ak.dev.khi_backend.khi_app.model.service.Service.builder()
                        .serviceType("Program")
                        .location("Sulaymaniyah & Online")
                        .active(true)
                        .publishedAt(LocalDateTime.now().minusDays(15))
                        .build();
        s6.addContent(ServiceContent.builder()
                .languageCode("CKB")
                .title("بەرنامەی تۆمارکردنی مێژووی زارەکی")
                .description("""
                    <h2>مێژووی زارەکی</h2>
                    <p>بەرنامەیەک بۆ تۆمارکردنی یادەوەری و چیرۆکی کەسانی تەمەن بەرز پێش لەناوچوونیان.</p>
                    <img src=\"%s\" alt=\"چاوپێکەوتن\">
                    <audio controls src=\"%s\"></audio>
                    <p>ئەم بەرنامەیە گرنگییەکی تایبەتی بە پاراستنی بیرەوەرییە کۆمەڵایەتییەکان دەدات.</p>
                    """.formatted(IMG_INTERVIEW, AUD_SONG_3))
                .build());
        s6.addContent(ServiceContent.builder()
                .languageCode("KMR")
                .title("Bernameya tomarkirina dîroka devkî")
                .description("""
                    <h2>Dîroka devkî</h2>
                    <p>Bernameyek ji bo tomarkirina bîranîn û çîrokên kesên temen-bilind berî windabûna wan.</p>
                    <img src=\"%s\" alt=\"Hevpeyvîn\">
                    <audio controls src=\"%s\"></audio>
                    <p>Ev bername girîngiyeke taybet bi parastina bîranînên civakî dide.</p>
                    """.formatted(IMG_INTERVIEW, AUD_SONG_3))
                .build());

        ak.dev.khi_backend.khi_app.model.service.Service s7 =
                ak.dev.khi_backend.khi_app.model.service.Service.builder()
                        .serviceType("Event")
                        .location("Duhok, Cultural Center")
                        .active(true)
                        .publishedAt(LocalDateTime.now().minusDays(8))
                        .build();
        s7.addContent(ServiceContent.builder()
                .languageCode("CKB")
                .title("فێستیڤاڵی ساڵانەی کتێبی کوردی")
                .description("""
                    <h2>فێستیڤاڵی کتێب</h2>
                    <p>فێستیڤاڵێکی ساڵانە کە نووسەران و وەشانخانە و خوێنەرانی کورد کۆ دەکاتەوە لە یەک شوێن.</p>
                    <img src=\"%s\" alt=\"کتێب\">
                    <video controls src=\"%s\"></video>
                    <h3>چالاکییەکان</h3>
                    <ul>
                        <li>پیشانگای کتێب</li>
                        <li>واژۆکردنی کتێب لەگەڵ نووسەران</li>
                        <li>گفتوگۆی ئەدەبی</li>
                    </ul>
                    """.formatted(IMG_BOOKS_SHELF, VID_ELEPHANTS))
                .build());
        s7.addContent(ServiceContent.builder()
                .languageCode("KMR")
                .title("Festîvala salane ya pirtûka kurdî")
                .description("""
                    <h2>Festîvala pirtûkê</h2>
                    <p>Festîvaleke salane ku nivîskar, weşanxane û xwendevanên kurd li yek cihî dicivîne.</p>
                    <img src=\"%s\" alt=\"Pirtûk\">
                    <video controls src=\"%s\"></video>
                    <h3>Çalakî</h3>
                    <ul>
                        <li>Pêşangeha pirtûkê</li>
                        <li>Îmzekirina pirtûkan bi nivîskaran re</li>
                        <li>Gotûbêja edebî</li>
                    </ul>
                    """.formatted(IMG_BOOKS_SHELF, VID_ELEPHANTS))
                .build());

        ak.dev.khi_backend.khi_app.model.service.Service s8 =
                ak.dev.khi_backend.khi_app.model.service.Service.builder()
                        .serviceType("Workshop")
                        .location("Online")
                        .active(true)
                        .publishedAt(LocalDateTime.now().minusDays(3))
                        .build();
        s8.addContent(ServiceContent.builder()
                .languageCode("CKB")
                .title("وۆرکشۆپی وەرگێڕان نێوان سۆرانی و کرمانجی")
                .description("<p>وۆرکشۆپێکی ئۆنلاین بۆ فێربوونی ئەساسەکانی وەرگێڕان لە نێوان دوو زاراوەکەی کوردی.</p><img src=\"%s\"><audio controls src=\"%s\"></audio>".formatted(IMG_OPEN_BOOK, AUD_SONG_5))
                .build());
        s8.addContent(ServiceContent.builder()
                .languageCode("KMR")
                .title("Atolyeya wergerê di navbera soranî û kurmancî de")
                .description("<p>Atolyeyeke onlîne ji bo fêrbûna bingehên wergerê di navbera her du zaravayên kurdî de.</p><img src=\"%s\"><audio controls src=\"%s\"></audio>".formatted(IMG_OPEN_BOOK, AUD_SONG_5))
                .build());

        ak.dev.khi_backend.khi_app.model.service.Service s9 =
                ak.dev.khi_backend.khi_app.model.service.Service.builder()
                        .serviceType("Program")
                        .location("Erbil & Sulaymaniyah")
                        .active(true)
                        .publishedAt(LocalDateTime.now().minusDays(12))
                        .build();
        s9.addContent(ServiceContent.builder()
                .languageCode("CKB")
                .title("بەرنامەی بورسی توێژینەوە بۆ گەنجان")
                .description("""
                    <h2>بورسی توێژینەوە</h2>
                    <p>بەرنامەیەک بۆ پشتیوانی دارایی و ئەکادیمی لە توێژەرە گەنجەکانی کورد کە کار لەسەر مێژوو و کولتووری کوردی دەکەن.</p>
                    <img src=\"%s\" alt=\"بورس\">
                    <h3>مەرجەکان</h3>
                    <ul>
                        <li>تەمەن لە نێوان ٢٢ بۆ ٣٥ ساڵ</li>
                        <li>پرۆژەی توێژینەوەی پەسەندکراو</li>
                        <li>پابەندبوون بە باڵوکردنەوەی ئەنجامەکان</li>
                    </ul>
                    <video controls src=\"%s\"></video>
                    <p><a href=\"%s\">فۆرمی داواکاری بورس (PDF)</a></p>
                    """.formatted(IMG_TEAM, VID_ELEPHANTS, PDF_DUMMY))
                .build());
        s9.addContent(ServiceContent.builder()
                .languageCode("KMR")
                .title("Bernameya bûrsa lêkolînê ji bo ciwanan")
                .description("""
                    <h2>Bûrsa lêkolînê</h2>
                    <p>Bernameyek ji bo piştgiriya darayî û akademîk a lêkolînerên ciwan ên kurd ku li ser dîrok û çanda kurdî dixebitin.</p>
                    <img src=\"%s\" alt=\"Bûrs\">
                    <h3>Merc</h3>
                    <ul>
                        <li>Temen di navbera 22 û 35 salî de</li>
                        <li>Projeya lêkolînê ya pejirandî</li>
                        <li>Girêdayîbûn bi belavkirina encaman</li>
                    </ul>
                    <video controls src=\"%s\"></video>
                    <p><a href=\"%s\">Forma daxwaza bûrsê (PDF)</a></p>
                    """.formatted(IMG_TEAM, VID_ELEPHANTS, PDF_DUMMY))
                .build());

        ak.dev.khi_backend.khi_app.model.service.Service s10 =
                ak.dev.khi_backend.khi_app.model.service.Service.builder()
                        .serviceType("Event")
                        .location("Erbil, KHI Hall")
                        .active(true)
                        .publishedAt(LocalDateTime.now().minusDays(1))
                        .build();
        s10.addContent(ServiceContent.builder()
                .languageCode("CKB")
                .title("شەوی شیعر و مۆسیقای کوردی")
                .description("""
                    <h2>شەوی هونەری</h2>
                    <p>شەوێکی هونەری مانگانە کە تێیدا شاعیران و مۆسیقاژەنانی کورد بەرهەمەکانیان پێشکەش دەکەن.</p>
                    <img src=\"%s\" alt=\"شەوی شیعر\">
                    <audio controls src=\"%s\"></audio>
                    <video controls src=\"%s\"></video>
                    <p>چالاکییەکە بۆ گشت کراوەیە و بەخۆڕاییە.</p>
                    """.formatted(IMG_STAGE, AUD_SONG_1, VID_TEARS))
                .build());
        s10.addContent(ServiceContent.builder()
                .languageCode("KMR")
                .title("Şeva helbest û muzîka kurdî")
                .description("""
                    <h2>Şeva hunerî</h2>
                    <p>Şeveke hunerî ya mehane ku tê de helbestvan û muzîkjenên kurd berhemên xwe pêşkêş dikin.</p>
                    <img src=\"%s\" alt=\"Şeva helbestê\">
                    <audio controls src=\"%s\"></audio>
                    <video controls src=\"%s\"></video>
                    <p>Çalakî ji bo herkesî vekirî û belaş e.</p>
                    """.formatted(IMG_STAGE, AUD_SONG_1, VID_TEARS))
                .build());

        serviceRepository.saveAll(List.of(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10));
        log.info("[seed:Service] inserted 10 rows");
    }

    // =========================================================================
    // 4) NEWS  — 10 articles across 3 categories / 4 sub-categories
    // =========================================================================

    @Transactional
    public void seedNews() {
        if (newsRepository.count() > 0) {
            log.info("[seed:News] table not empty — skipping");
            return;
        }

        NewsCategory catCulture = newsCategoryRepository.save(NewsCategory.builder()
                .nameCkb("کلتوور").nameKmr("Çand").build());
        NewsCategory catHistory = newsCategoryRepository.save(NewsCategory.builder()
                .nameCkb("مێژوو").nameKmr("Dîrok").build());
        NewsCategory catLiterature = newsCategoryRepository.save(NewsCategory.builder()
                .nameCkb("ئەدەب").nameKmr("Edebiyat").build());

        NewsSubCategory subFolk = newsSubCategoryRepository.save(NewsSubCategory.builder()
                .nameCkb("فۆلکلۆر").nameKmr("Folklor").category(catCulture).build());
        NewsSubCategory subAncient = newsSubCategoryRepository.save(NewsSubCategory.builder()
                .nameCkb("مێژووی کۆن").nameKmr("Dîroka kevn").category(catHistory).build());
        NewsSubCategory subPoetry = newsSubCategoryRepository.save(NewsSubCategory.builder()
                .nameCkb("شیعر").nameKmr("Helbest").category(catLiterature).build());
        NewsSubCategory subNovel = newsSubCategoryRepository.save(NewsSubCategory.builder()
                .nameCkb("ڕۆمان").nameKmr("Roman").category(catLiterature).build());

        News n1 = News.builder()
                .coverUrl(IMG_NEWSPAPER)
                .coverMediaType(MediaKind.IMAGE)
                .datePublished(LocalDate.now().minusDays(2))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(NewsContent.builder()
                        .title("کۆنفرانسێکی گەورە لە مێژووی کورد لە هەولێر بەڕێوەچوو")
                        .description("""
                            <p>لە دوێنێوە ، کۆنفرانسێکی نێودەوڵەتی لە دامەزراوەکەمان لە هەولێر بەڕێوەچوو کە تایبەت بوو بە لێکۆڵینەوەی مێژووی نوێی کورد.</p>
                            <img src=\"%s\" alt=\"کۆنفرانس\">
                            <p>زیاتر لە ٢٠٠ توێژەر و ئەکادیمی لە ١٥ وڵاتی جیاوازەوە بەشدارییان کرد.</p>
                            <video controls src=\"%s\"></video>
                            <h3>سەرنجی سەرەکی</h3>
                            <ul>
                                <li>پێشکەشکردنی ٣٥ توێژینەوەی نوێ</li>
                                <li>دەرکردنی ٣ کتێبی ئەکادیمی</li>
                                <li>دامەزراندنی شەبەکەی هاوکاری نێودەوڵەتی</li>
                            </ul>
                            <p><a href=\"%s\">ڕاپۆرتی تەواوی کۆنفرانس (PDF)</a></p>
                            """.formatted(IMG_STAGE, VID_BIG_BUCK_BUNNY, PDF_DUMMY))
                        .build())
                .kmrContent(NewsContent.builder()
                        .title("Konferanseke mezin a dîroka kurdî li Hewlêrê pêk hat")
                        .description("""
                            <p>Duh, konferanseke navneteweyî li saziya me ya li Hewlêrê pêk hat ku taybet bû bi lêkolîna dîroka nû ya kurdî.</p>
                            <img src=\"%s\" alt=\"Konferans\">
                            <p>Zêdetir ji 200 lêkolîner û akademîsyen ji 15 welatên cuda beşdar bûn.</p>
                            <video controls src=\"%s\"></video>
                            <h3>Xalên sereke</h3>
                            <ul>
                                <li>Pêşkêşkirina 35 lêkolînên nû</li>
                                <li>Çapkirina 3 pirtûkên akademîk</li>
                                <li>Damezrandina tora hevkariyê ya navneteweyî</li>
                            </ul>
                            <p><a href=\"%s\">Rapora tam a konferansê (PDF)</a></p>
                            """.formatted(IMG_STAGE, VID_BIG_BUCK_BUNNY, PDF_DUMMY))
                        .build())
                .tagsCkb(new LinkedHashSet<>(Set.of("کۆنفرانس", "مێژوو", "هەولێر")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Konferans", "Dîrok", "Hewlêr")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("کوردستان", "توێژینەوە", "ئەکادیمی")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Kurdistan", "Lêkolîn", "Akademîk")))
                .category(catHistory).subCategory(subAncient)
                .build();

        News n2 = News.builder()
                .coverUrl(VID_ELEPHANTS)
                .coverMediaType(MediaKind.VIDEO)
                .coverThumbnailUrl(IMG_CONCERT)
                .datePublished(LocalDate.now().minusDays(5))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(NewsContent.builder()
                        .title("ئاهەنگی گۆرانی کۆنی کوردی لە سلێمانی")
                        .description("""
                            <p>ئاهەنگێکی هونەری لە سلێمانی بەڕێوەچوو کە تایبەت بوو بە گۆرانی کۆنی کوردی.</p>
                            <img src=\"%s\" alt=\"کۆنسێرت\">
                            <video controls src=\"%s\"></video>
                            <audio controls src=\"%s\"></audio>
                            <p>گۆرانیبێژە ناودارەکانی وەکو شاژنە ، مەزهەر خالقی و حەسەن زیرەک یاد کرانەوە.</p>
                            """.formatted(IMG_CONCERT, VID_ELEPHANTS, AUD_SONG_2))
                        .build())
                .kmrContent(NewsContent.builder()
                        .title("Şahiya stranên kevn ên kurdî li Silêmaniyê")
                        .description("""
                            <p>Şahiyeke hunerî li Silêmaniyê pêk hat ku taybet bû bi stranên kevn ên kurdî.</p>
                            <img src=\"%s\" alt=\"Konser\">
                            <video controls src=\"%s\"></video>
                            <audio controls src=\"%s\"></audio>
                            <p>Stranbêjên navdar ên wek Şahanê, Mezher Xaliqî û Hesen Zîrek hatin bibîranîn.</p>
                            """.formatted(IMG_CONCERT, VID_ELEPHANTS, AUD_SONG_2))
                        .build())
                .tagsCkb(new LinkedHashSet<>(Set.of("مۆسیقا", "کلتوور", "گۆرانی")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Muzîk", "Çand", "Stran")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("هونەر", "فۆلکلۆر")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Huner", "Folklor")))
                .category(catCulture).subCategory(subFolk)
                .mediaGallery(List.of(
                        MediaItem.builder().url(IMG_CONCERT).kind(MediaKind.IMAGE)
                                .captionCkb("ئاهەنگەکە").captionKmr("Şahî").sortOrder(0).build(),
                        MediaItem.builder().url(AUD_SONG_1).kind(MediaKind.AUDIO)
                                .thumbnailUrl(IMG_AUDIO_STUDIO)
                                .captionCkb("نموونە گۆرانی").captionKmr("Stranek nimûne").sortOrder(1).build()
                ))
                .build();

        News n3 = News.builder()
                .coverUrl(IMG_OPEN_BOOK)
                .coverMediaType(MediaKind.IMAGE)
                .datePublished(LocalDate.now().minusDays(10))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB)))
                .ckbContent(NewsContent.builder()
                        .title("کتێبی نوێی شیعری سۆرانی باڵو کرایەوە")
                        .description("""
                            <p>کتێبێکی نوێی شیعر بە زمانی سۆرانی لە لایەن شاعیر و رۆژنامەنووسی گەنج ، ئامێد قارەمانەوە باڵو کرایەوە.</p>
                            <img src=\"%s\" alt=\"کتێب\">
                            <p>کتێبەکە ٢٠٠ شیعر لەخۆ دەگرێت کە لە کاتژمێری دواییدا نووسراون.</p>
                            <a href=\"%s\">پیشاندانی نموونە (PDF)</a>
                            """.formatted(IMG_BOOKS_SHELF, PDF_DUMMY))
                        .build())
                .kmrContent(NewsContent.builder().title("").description("").build())
                .tagsCkb(new LinkedHashSet<>(Set.of("شیعر", "نووسەری گەنج")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("ئەدەب", "وەشان")))
                .category(catLiterature).subCategory(subPoetry)
                .build();

        News n4 = News.builder()
                .coverUrl(IMG_ERBIL_CITADEL)
                .coverMediaType(MediaKind.IMAGE)
                .datePublished(LocalDate.now().minusDays(15))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(NewsContent.builder()
                        .title("قەڵای هەولێر و جۆرە دۆکیومێنتە مێژووییە نوێیەکان")
                        .description("""
                            <h2>دۆزینەوەی نوێ</h2>
                            <p>تیمێکی توێژەران لە قەڵای هەولێر چەند دۆکیومێنتی مێژوویی نوێیان دۆزیوەتەوە کە دەگەڕێنەوە بۆ سەردەمی عوسمانیەکان.</p>
                            <img src=\"%s\" alt=\"قەڵا\">
                            <video controls src=\"%s\"></video>
                            <p>ئەم دۆکیومێنتانە ڕووناکی دەخەنە سەر چەند ڕووداوی گرنگ لە مێژووی شاری هەولێر.</p>
                            """.formatted(IMG_OLD_DOCUMENT, VID_SINTEL))
                        .build())
                .kmrContent(NewsContent.builder()
                        .title("Kelaya Hewlêrê û belgenameyên dîrokî yên nû")
                        .description("""
                            <h2>Vedîtinên nû</h2>
                            <p>Tîmek lêkolîner li Kelaya Hewlêrê çend belgenameyên dîrokî yên nû dîtine ku digihîjin serdema osmaniyan.</p>
                            <img src=\"%s\" alt=\"Kela\">
                            <video controls src=\"%s\"></video>
                            <p>Ev belgename ronahiyê dixin ser çend bûyerên girîng ên dîroka bajarê Hewlêrê.</p>
                            """.formatted(IMG_OLD_DOCUMENT, VID_SINTEL))
                        .build())
                .tagsCkb(new LinkedHashSet<>(Set.of("قەڵا", "مێژوو")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Kela", "Dîrok")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("هەولێر", "عوسمانی")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Hewlêr", "Osmanî")))
                .category(catHistory).subCategory(subAncient)
                .build();

        News n5 = News.builder()
                .coverUrl(IMG_INTERVIEW)
                .coverMediaType(MediaKind.IMAGE)
                .datePublished(LocalDate.now().minusDays(20))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(NewsContent.builder()
                        .title("چاوپێکەوتنێکی تایبەت لەگەڵ نووسەری بەناوبانگ شێرکۆ بێکەس")
                        .description("""
                            <p>چاوپێکەوتنێکی تایبەتمان بڵاو دەکەینەوە لەگەڵ کاکە شێرکۆ بێکەس، یەکێک لە ناودارترین شاعیرانی کورد لە سەدەی ٢١.</p>
                            <img src=\"%s\" alt=\"چاوپێکەوتن\">
                            <audio controls src=\"%s\"></audio>
                            <p>لەم چاوپێکەوتنەدا، شێرکۆ ڕایوەکانی خۆی لەسەر داهاتووی شیعری کورد و ڕۆڵی نووسەرانی گەنج پێشکەش دەکات.</p>
                            """.formatted(IMG_INTERVIEW, AUD_SONG_3))
                        .build())
                .kmrContent(NewsContent.builder()
                        .title("Hevpeyvîneke taybet bi nivîskarê navdar Şêrko Bêkes re")
                        .description("""
                            <p>Em hevpeyvîneke taybet bi kak Şêrko Bêkes re, yek ji helbestvanên herî navdar ên kurd ên sedsala 21ê, belav dikin.</p>
                            <img src=\"%s\" alt=\"Hevpeyvîn\">
                            <audio controls src=\"%s\"></audio>
                            <p>Di vê hevpeyvînê de, Şêrko nêrînên xwe li ser pêşeroja helbesta kurdî û rola nivîskarên ciwan pêşkêş dike.</p>
                            """.formatted(IMG_INTERVIEW, AUD_SONG_3))
                        .build())
                .tagsCkb(new LinkedHashSet<>(Set.of("چاوپێکەوتن", "شاعیر")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Hevpeyvîn", "Helbestvan")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("شیعر", "شێرکۆ")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Helbest", "Şêrko")))
                .category(catLiterature).subCategory(subNovel)
                .build();

        News n6 = News.builder()
                .coverUrl(IMG_AUDIO_STUDIO)
                .coverMediaType(MediaKind.IMAGE)
                .datePublished(LocalDate.now().minusDays(25))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(NewsContent.builder()
                        .title("ستۆدیۆیەکی نوێ بۆ تۆمارکردنی دەنگی هونەرمەندە تەمەن بەرزەکان کرایەوە")
                        .description("""
                            <p>دامەزراوەکەمان ستۆدیۆیەکی نوێی پیشەیی لە سلێمانی کردەوە کە تایبەتە بە تۆمارکردنی دەنگی هونەرمەندە تەمەن بەرزەکان.</p>
                            <img src=\"%s\" alt=\"ستۆدیۆ\">
                            <audio controls src=\"%s\"></audio>
                            <p>ئامانج پاراستنی دەنگی ئەو هونەرمەندانەیە کە لەوانەیە لە داهاتوودا لەدەست بدرێن.</p>
                            """.formatted(IMG_AUDIO_STUDIO, AUD_SONG_4))
                        .build())
                .kmrContent(NewsContent.builder()
                        .title("Stûdyoyeke nû ji bo tomarkirina dengê hunermendên temen-bilind hat vekirin")
                        .description("""
                            <p>Saziya me stûdyoyeke nû ya profesyonel li Silêmaniyê vekir ku taybet e ji bo tomarkirina dengê hunermendên temen-bilind.</p>
                            <img src=\"%s\" alt=\"Stûdyo\">
                            <audio controls src=\"%s\"></audio>
                            <p>Armanc parastina dengê wan hunermendan e ku dibe ku di pêşerojê de winda bibin.</p>
                            """.formatted(IMG_AUDIO_STUDIO, AUD_SONG_4))
                        .build())
                .tagsCkb(new LinkedHashSet<>(Set.of("ستۆدیۆ", "تۆمارکردن")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Stûdyo", "Tomarkirin")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("دەنگ", "پاراستن")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Deng", "Parastin")))
                .category(catCulture).subCategory(subFolk)
                .build();

        News n7 = News.builder()
                .coverUrl(IMG_ARCHIVE)
                .coverMediaType(MediaKind.IMAGE)
                .datePublished(LocalDate.now().minusDays(30))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(NewsContent.builder()
                        .title("پرۆژەی دیجیتاڵکردنی ئەرشیف گەیشتە ١٠،٠٠٠ دۆکیومێنت")
                        .description("""
                            <h2>سەرکەوتنێکی نوێ</h2>
                            <p>پرۆژەی دیجیتاڵکردنی ئەرشیفی مێژوویی کوردی گەیشتە سنووری ١٠،٠٠٠ دۆکیومێنتی دیجیتاڵکراو.</p>
                            <img src=\"%s\" alt=\"ئەرشیف\">
                            <video controls src=\"%s\"></video>
                            <p>ئەم دۆکیومێنتانە ئێستا بەردەستن بۆ توێژەران بەشێوەیەکی ئۆنلاین.</p>
                            """.formatted(IMG_ARCHIVE, VID_FOR_BIGGER))
                        .build())
                .kmrContent(NewsContent.builder()
                        .title("Projeya dîjîtalkirina arşîvê gihîşt 10,000 belgenameyan")
                        .description("""
                            <h2>Serkeftineke nû</h2>
                            <p>Projeya dîjîtalkirina arşîva dîrokî ya kurdî gihîşt sînorê 10,000 belgenameyên dîjîtalkirî.</p>
                            <img src=\"%s\" alt=\"Arşîv\">
                            <video controls src=\"%s\"></video>
                            <p>Ev belgename niha bi awayekî onlîne ji bo lêkolîneran berdest in.</p>
                            """.formatted(IMG_ARCHIVE, VID_FOR_BIGGER))
                        .build())
                .tagsCkb(new LinkedHashSet<>(Set.of("ئەرشیف", "دیجیتاڵ")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Arşîv", "Dîjîtal")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("پاراستن", "میرات")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Parastin", "Mîrate")))
                .category(catHistory).subCategory(subAncient)
                .build();

        News n8 = News.builder()
                .coverUrl(IMG_FOLK_DANCE)
                .coverMediaType(MediaKind.IMAGE)
                .datePublished(LocalDate.now().minusDays(35))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(NewsContent.builder()
                        .title("فێستیڤاڵی فۆلکلۆری کوردی لە دهۆک بەرپا کرا")
                        .description("""
                            <p>فێستیڤاڵێکی گەورەی فۆلکلۆری کوردی لە شاری دهۆک بەرپا کرا کە تێیدا گرووپە فۆلکلۆریەکان لە سەرتاسەری کوردستان بەشدارییان کرد.</p>
                            <img src=\"%s\" alt=\"فۆلکلۆر\">
                            <video controls src=\"%s\"></video>
                            <audio controls src=\"%s\"></audio>
                            <p>سەماکانی چۆپی و هەڵپەڕکێ بەشێکی سەرەکی فێستیڤاڵەکە بوون.</p>
                            """.formatted(IMG_FOLK_DANCE, VID_TEARS, AUD_SONG_5))
                        .build())
                .kmrContent(NewsContent.builder()
                        .title("Festîvala folklora kurdî li Duhokê hat li dar xistin")
                        .description("""
                            <p>Festîvaleke mezin a folklora kurdî li bajarê Duhokê hat li dar xistin ku tê de komên folklorîk ji seranserê Kurdistanê beşdar bûn.</p>
                            <img src=\"%s\" alt=\"Folklor\">
                            <video controls src=\"%s\"></video>
                            <audio controls src=\"%s\"></audio>
                            <p>Govend û dîlanên kurdî beşeke sereke ya festîvalê bûn.</p>
                            """.formatted(IMG_FOLK_DANCE, VID_TEARS, AUD_SONG_5))
                        .build())
                .tagsCkb(new LinkedHashSet<>(Set.of("فێستیڤاڵ", "فۆلکلۆر", "دهۆک")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Festîval", "Folklor", "Duhok")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("سەما", "کلتوور")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Govend", "Çand")))
                .category(catCulture).subCategory(subFolk)
                .mediaGallery(List.of(
                        MediaItem.builder().url(IMG_FOLK_DANCE).kind(MediaKind.IMAGE)
                                .captionCkb("سەمای فۆلکلۆری").captionKmr("Govenda folklorîk").sortOrder(0).build(),
                        MediaItem.builder().url(IMG_TRADITIONAL).kind(MediaKind.IMAGE)
                                .captionCkb("جلوبەرگی نەتەوەیی").captionKmr("Kincê neteweyî").sortOrder(1).build()
                ))
                .build();

        News n9 = News.builder()
                .coverUrl(IMG_BOOKS_SHELF)
                .coverMediaType(MediaKind.IMAGE)
                .datePublished(LocalDate.now().minusDays(40))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(NewsContent.builder()
                        .title("ڕۆمانێکی نوێی مێژوویی لەسەر کۆماری مەهاباد باڵو کرایەوە")
                        .description("""
                            <p>ڕۆمانێکی نوێی مێژوویی لەسەر کۆماری مەهاباد ١٩٤٦ لە لایەن نووسەرێکی ناوداری کوردەوە باڵو کرایەوە.</p>
                            <img src=\"%s\" alt=\"کتێب\">
                            <p>ڕۆمانەکە تێکەڵەیەکە لە ڕووداوی مێژوویی و چیرۆکی خەیاڵی کە ژیانی ئەو سەردەمە دەگێڕێتەوە.</p>
                            <a href=\"%s\">نموونەیەک لە ڕۆمانەکە (PDF)</a>
                            """.formatted(IMG_BOOKS_SHELF, PDF_DUMMY))
                        .build())
                .kmrContent(NewsContent.builder()
                        .title("Romaneke nû ya dîrokî li ser Komara Mehabadê hat weşandin")
                        .description("""
                            <p>Romaneke nû ya dîrokî li ser Komara Mehabadê ya 1946an ji aliyê nivîskarekî kurd ê navdar ve hat weşandin.</p>
                            <img src=\"%s\" alt=\"Pirtûk\">
                            <p>Roman tevliheviyek e ji bûyerên dîrokî û çîroka xeyalî ku jiyana wê serdemê vedibêje.</p>
                            <a href=\"%s\">Nimûneyek ji romanê (PDF)</a>
                            """.formatted(IMG_BOOKS_SHELF, PDF_DUMMY))
                        .build())
                .tagsCkb(new LinkedHashSet<>(Set.of("ڕۆمان", "مێژوو")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Roman", "Dîrok")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("مەهاباد", "ئەدەب")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Mehabad", "Edebiyat")))
                .category(catLiterature).subCategory(subNovel)
                .build();

        News n10 = News.builder()
                .coverUrl(IMG_MOUNTAINS_KRD)
                .coverMediaType(MediaKind.IMAGE)
                .datePublished(LocalDate.now().minusDays(50))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(NewsContent.builder()
                        .title("توێژینەوەیەکی نوێ لەسەر زاراوەی کرمانجی باڵو کرایەوە")
                        .description("""
                            <h2>توێژینەوەی زمانەوانی</h2>
                            <p>تیمی زمانەوانی دامەزراوەکەمان توێژینەوەیەکی فراوانی لەسەر زاراوەی کرمانجی و گۆڕانکارییەکانی باڵو کردەوە.</p>
                            <img src=\"%s\" alt=\"چیا\">
                            <video controls src=\"%s\"></video>
                            <p>توێژینەوەکە چەندین دیالێکتی ناوچەیی کرمانجی شیکار دەکات.</p>
                            <a href=\"%s\">توێژینەوەی تەواو (PDF)</a>
                            """.formatted(IMG_MOUNTAINS_KRD, VID_ELEPHANTS, PDF_DUMMY))
                        .build())
                .kmrContent(NewsContent.builder()
                        .title("Lêkolîneke nû li ser zaravayê kurmancî hat weşandin")
                        .description("""
                            <h2>Lêkolîna zimanzanî</h2>
                            <p>Tîma zimanzaniyê ya saziya me lêkolîneke berfireh li ser zaravayê kurmancî û guherînên wê weşand.</p>
                            <img src=\"%s\" alt=\"Çiya\">
                            <video controls src=\"%s\"></video>
                            <p>Lêkolîn gelek diyalektên herêmî yên kurmancî vedikole.</p>
                            <a href=\"%s\">Lêkolîna tam (PDF)</a>
                            """.formatted(IMG_MOUNTAINS_KRD, VID_ELEPHANTS, PDF_DUMMY))
                        .build())
                .tagsCkb(new LinkedHashSet<>(Set.of("زمان", "کرمانجی", "توێژینەوە")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Ziman", "Kurmancî", "Lêkolîn")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("زمانەوانی", "دیالێکت")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Zimanzanî", "Diyalekt")))
                .category(catHistory).subCategory(subAncient)
                .build();

        newsRepository.saveAll(List.of(n1, n2, n3, n4, n5, n6, n7, n8, n9, n10));
        log.info("[seed:News] inserted 10 rows (3 categories, 4 sub-categories)");
    }

    // =========================================================================
    // 5) PROJECT  — 10 projects
    // =========================================================================

    @Transactional
    public void seedProjects() {
        if (projectRepository.count() > 0) {
            log.info("[seed:Project] table not empty — skipping");
            return;
        }

        ProjectTag tagArchive   = ensureTag("Archive");
        ProjectTag tagDigital   = ensureTag("Digital");
        ProjectTag tagHistory   = ensureTag("History");
        ProjectTag tagFolklore  = ensureTag("Folklore");
        ProjectTag tagLanguage  = ensureTag("Language");
        ProjectTag tagMusic     = ensureTag("Music");
        ProjectTag tagEducation = ensureTag("Education");

        ProjectKeyword kwKurdish   = ensureKeyword("Kurdish");
        ProjectKeyword kwHeritage  = ensureKeyword("Heritage");
        ProjectKeyword kwResearch  = ensureKeyword("Research");
        ProjectKeyword kwCulture   = ensureKeyword("Culture");

        Project p1 = Project.builder()
                .coverUrl(IMG_ARCHIVE)
                .coverMediaType(MediaKind.IMAGE)
                .ckbContent(ProjectContentBlock.builder()
                        .title("دیجیتاڵکردنی ئەرشیفی مێژوویی کوردی")
                        .description("""
                            <h2>پرۆژەی پێشەنگ</h2>
                            <p>پرۆژەیەکی ٥ ساڵە بۆ دیجیتاڵکردنی زیاتر لە ١٠،٠٠٠ دۆکیومێنتی مێژوویی کۆنی کوردی کە پەراوێز خراون.</p>
                            <img src=\"%s\" alt=\"ئەرشیف\">
                            <video controls src=\"%s\"></video>
                            <h3>قۆناغەکانی پرۆژە</h3>
                            <ol>
                                <li>کۆکردنەوەی دۆکیومێنتەکان</li>
                                <li>وەرگێڕان و چاکسازی</li>
                                <li>دیجیتاڵکردن</li>
                                <li>باڵوکردنەوەی ئۆنلاین</li>
                            </ol>
                            <p><a href=\"%s\">پلانی تەواوی پرۆژە</a></p>
                            """.formatted(IMG_OLD_DOCUMENT, VID_BIG_BUCK_BUNNY, PDF_DUMMY))
                        .location("هەولێر")
                        .build())
                .kmrContent(ProjectContentBlock.builder()
                        .title("Dîjîtalkirina arşîva dîrokî ya kurdî")
                        .description("""
                            <h2>Projeya pêşeng</h2>
                            <p>Projeyeke 5 salî ji bo dîjîtalkirina zêdetirî 10,000 belgenameyên dîrokî yên kevn ên kurdî ku ji bîr çûne.</p>
                            <img src=\"%s\" alt=\"Arşîv\">
                            <video controls src=\"%s\"></video>
                            <h3>Qonaxên projeyê</h3>
                            <ol>
                                <li>Berhevkirina belgenameyan</li>
                                <li>Wergerandin û rastkirin</li>
                                <li>Dîjîtalkirin</li>
                                <li>Belavkirina onlîne</li>
                            </ol>
                            <p><a href=\"%s\">Plana tam a projeyê</a></p>
                            """.formatted(IMG_OLD_DOCUMENT, VID_BIG_BUCK_BUNNY, PDF_DUMMY))
                        .location("Hewlêr")
                        .build())
                .projectTypeCkb("ئەرشیفکردن")
                .projectTypeKmr("Arşîvkirin")
                .status(ProjectStatus.ONGOING)
                .projectDate(LocalDate.now().minusMonths(6))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of(tagArchive, tagDigital, tagHistory)))
                .tagsKmr(new LinkedHashSet<>(Set.of(tagArchive, tagDigital)))
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwKurdish, kwHeritage, kwResearch)))
                .keywordsKmr(new LinkedHashSet<>(Set.of(kwKurdish, kwHeritage)))
                .mediaGallery(List.of(
                        MediaItem.builder().url(IMG_ARCHIVE).kind(MediaKind.IMAGE).sortOrder(0).build(),
                        MediaItem.builder().url(VID_FOR_BIGGER).kind(MediaKind.VIDEO)
                                .thumbnailUrl(IMG_OLD_DOCUMENT).sortOrder(1).build()
                ))
                .build();

        Project p2 = Project.builder()
                .coverUrl(IMG_FOLK_DANCE)
                .coverMediaType(MediaKind.IMAGE)
                .ckbContent(ProjectContentBlock.builder()
                        .title("کۆکردنەوەی فۆلکلۆری زاراوەی بادینی")
                        .description("""
                            <p>پرۆژەیەکی تایبەت بۆ کۆکردنەوەی فۆلکلۆری زاراوەی بادینی لە شارەکانی دهۆک ، زاخۆ و سۆران.</p>
                            <img src=\"%s\" alt=\"فۆلکلۆر\">
                            <audio controls src=\"%s\"></audio>
                            <p>تا ئێستا زیاتر لە ٥٠٠ چیرۆکی فۆلکلۆری و ١٠٠٠ گۆرانی فۆلکلۆری تۆمار کراون.</p>
                            """.formatted(IMG_FOLK_DANCE, AUD_SONG_4))
                        .location("دهۆک، زاخۆ، سۆران")
                        .build())
                .kmrContent(ProjectContentBlock.builder()
                        .title("Berhevkirina folklora zaravayê behdînî")
                        .description("""
                            <p>Projeyeke taybet ji bo berhevkirina folklora zaravayê behdînî li bajarên Duhok, Zaxo û Soranê.</p>
                            <img src=\"%s\" alt=\"Folklor\">
                            <audio controls src=\"%s\"></audio>
                            <p>Heta niha zêdetir ji 500 çîrokên folklorîk û 1000 stranên folklorîk hatine tomarkirin.</p>
                            """.formatted(IMG_FOLK_DANCE, AUD_SONG_4))
                        .location("Duhok, Zaxo, Soran")
                        .build())
                .projectTypeCkb("فۆلکلۆر")
                .projectTypeKmr("Folklor")
                .status(ProjectStatus.ONGOING)
                .projectDate(LocalDate.now().minusMonths(3))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of(tagFolklore, tagLanguage)))
                .tagsKmr(new LinkedHashSet<>(Set.of(tagFolklore, tagLanguage)))
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwKurdish, kwHeritage)))
                .keywordsKmr(new LinkedHashSet<>(Set.of(kwKurdish, kwHeritage)))
                .build();

        Project p3 = Project.builder()
                .coverUrl(IMG_TRADITIONAL)
                .coverMediaType(MediaKind.IMAGE)
                .ckbContent(ProjectContentBlock.builder()
                        .title("بەرنامەی توێژینەوەی هونەری کوردی")
                        .description("""
                            <p>پرۆژەیەکی توێژینەوەی ٣ ساڵە لەسەر مێژووی هونەری کوردی لە سەدەکانی ١٩ و ٢٠.</p>
                            <img src=\"%s\" alt=\"هونەر\">
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_TRADITIONAL, VID_TEARS))
                        .location("هەولێر و سلێمانی")
                        .build())
                .kmrContent(ProjectContentBlock.builder()
                        .title("Bernameya lêkolîna hunera kurdî")
                        .description("""
                            <p>Projeyeke lêkolînî ya 3 salî li ser dîroka hunera kurdî di sedsalên 19 û 20 de.</p>
                            <img src=\"%s\" alt=\"Huner\">
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_TRADITIONAL, VID_TEARS))
                        .location("Hewlêr û Silêmanî")
                        .build())
                .projectTypeCkb("توێژینەوە")
                .projectTypeKmr("Lêkolîn")
                .status(ProjectStatus.COMPLETED)
                .projectDate(LocalDate.now().minusYears(1))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of(tagHistory)))
                .tagsKmr(new LinkedHashSet<>(Set.of(tagHistory)))
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwResearch)))
                .keywordsKmr(new LinkedHashSet<>(Set.of(kwResearch)))
                .build();

        Project p4 = Project.builder()
                .coverUrl(VID_BIG_BUCK_BUNNY)
                .coverMediaType(MediaKind.VIDEO)
                .coverThumbnailUrl(IMG_AUDIO_STUDIO)
                .ckbContent(ProjectContentBlock.builder()
                        .title("ستۆدیۆی تۆمارکردنی دەنگی هونەرمەندە کۆنەکان")
                        .description("""
                            <p>پرۆژەی دامەزراندنی ستۆدیۆیەکی پیشەیی بۆ تۆمارکردنی دەنگی هونەرمەندە تەمەن بەرزەکان پێش لەناوچوونی.</p>
                            <img src=\"%s\" alt=\"ستۆدیۆ\">
                            <audio controls src=\"%s\"></audio>
                            """.formatted(IMG_AUDIO_STUDIO, AUD_SONG_2))
                        .location("سلێمانی")
                        .build())
                .kmrContent(ProjectContentBlock.builder()
                        .title("Stûdyoya tomarkirina dengê hunermendên kevn")
                        .description("""
                            <p>Projeya damezrandina stûdyoyeke profesyonel ji bo tomarkirina dengê hunermendên temen-bilind berî windabûnê.</p>
                            <img src=\"%s\" alt=\"Stûdyo\">
                            <audio controls src=\"%s\"></audio>
                            """.formatted(IMG_AUDIO_STUDIO, AUD_SONG_2))
                        .location("Silêmanî")
                        .build())
                .projectTypeCkb("تۆمارکردن")
                .projectTypeKmr("Tomarkirin")
                .status(ProjectStatus.ONGOING)
                .projectDate(LocalDate.now().minusMonths(2))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of(tagFolklore)))
                .tagsKmr(new LinkedHashSet<>(Set.of(tagFolklore)))
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwHeritage)))
                .keywordsKmr(new LinkedHashSet<>(Set.of(kwHeritage)))
                .build();

        Project p5 = Project.builder()
                .coverUrl(IMG_BOOKS_SHELF)
                .coverMediaType(MediaKind.IMAGE)
                .ckbContent(ProjectContentBlock.builder()
                        .title("وەشاندنەوەی کتێبە کلاسیکیە کوردیەکان")
                        .description("""
                            <h2>پرۆژەی وەشان</h2>
                            <p>پرۆژەیەک بۆ چاپ و وەشاندنەوەی ئەو کتێبە کلاسیکیە کوردیانەی کە لەبەردەستدا نەماون.</p>
                            <img src=\"%s\" alt=\"کتێب\">
                            <p>تا ئێستا ٢٥ کتێبی کلاسیکی دووبارە چاپ کراونەتەوە.</p>
                            <a href=\"%s\">لیستی کتێبەکان (PDF)</a>
                            """.formatted(IMG_BOOKS_SHELF, PDF_DUMMY))
                        .location("هەولێر")
                        .build())
                .kmrContent(ProjectContentBlock.builder()
                        .title("Vejandina pirtûkên klasîk ên kurdî")
                        .description("""
                            <h2>Projeya weşanê</h2>
                            <p>Projeyek ji bo çap û vejandina wan pirtûkên klasîk ên kurdî ku êdî berdest nemane.</p>
                            <img src=\"%s\" alt=\"Pirtûk\">
                            <p>Heta niha 25 pirtûkên klasîk ji nû ve hatine çapkirin.</p>
                            <a href=\"%s\">Lîsteya pirtûkan (PDF)</a>
                            """.formatted(IMG_BOOKS_SHELF, PDF_DUMMY))
                        .location("Hewlêr")
                        .build())
                .projectTypeCkb("وەشان")
                .projectTypeKmr("Weşan")
                .status(ProjectStatus.ONGOING)
                .projectDate(LocalDate.now().minusMonths(8))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of(tagEducation, tagHistory)))
                .tagsKmr(new LinkedHashSet<>(Set.of(tagEducation, tagHistory)))
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwKurdish, kwResearch)))
                .keywordsKmr(new LinkedHashSet<>(Set.of(kwKurdish, kwResearch)))
                .build();

        Project p6 = Project.builder()
                .coverUrl(IMG_CONCERT)
                .coverMediaType(MediaKind.IMAGE)
                .ckbContent(ProjectContentBlock.builder()
                        .title("ئەرشیفی مۆسیقای کوردی")
                        .description("""
                            <p>پرۆژەیەک بۆ کۆکردنەوە و پاراستنی هەموو ئەو گۆرانیە کۆنە کوردیانەی لە سەردەمی ١٩٤٠ تا ١٩٩٠ تۆمار کراون.</p>
                            <img src=\"%s\" alt=\"مۆسیقا\">
                            <audio controls src=\"%s\"></audio>
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_CONCERT, AUD_SONG_1, VID_ELEPHANTS))
                        .location("سلێمانی و هەولێر")
                        .build())
                .kmrContent(ProjectContentBlock.builder()
                        .title("Arşîva muzîka kurdî")
                        .description("""
                            <p>Projeyek ji bo berhevkirin û parastina hemû wan stranên kevn ên kurdî yên ku di serdema 1940 heta 1990 de hatine tomarkirin.</p>
                            <img src=\"%s\" alt=\"Muzîk\">
                            <audio controls src=\"%s\"></audio>
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_CONCERT, AUD_SONG_1, VID_ELEPHANTS))
                        .location("Silêmanî û Hewlêr")
                        .build())
                .projectTypeCkb("ئەرشیفی دەنگی")
                .projectTypeKmr("Arşîva dengî")
                .status(ProjectStatus.ONGOING)
                .projectDate(LocalDate.now().minusMonths(4))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of(tagMusic, tagArchive)))
                .tagsKmr(new LinkedHashSet<>(Set.of(tagMusic, tagArchive)))
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwHeritage, kwCulture)))
                .keywordsKmr(new LinkedHashSet<>(Set.of(kwHeritage, kwCulture)))
                .mediaGallery(List.of(
                        MediaItem.builder().url(IMG_CONCERT).kind(MediaKind.IMAGE).sortOrder(0).build(),
                        MediaItem.builder().url(AUD_SONG_3).kind(MediaKind.AUDIO)
                                .thumbnailUrl(IMG_AUDIO_STUDIO).sortOrder(1).build()
                ))
                .build();

        Project p7 = Project.builder()
                .coverUrl(IMG_CALLIGRAPHY)
                .coverMediaType(MediaKind.IMAGE)
                .ckbContent(ProjectContentBlock.builder()
                        .title("پاراستنی هونەری خوسنۆڤی کوردی")
                        .description("""
                            <p>پرۆژەیەک بۆ تۆمارکردن و فێرکردنی هونەری خوسنۆڤی کوردی بۆ نەوەی نوێ.</p>
                            <img src=\"%s\" alt=\"خوسنۆڤی\">
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_CALLIGRAPHY, VID_SINTEL))
                        .location("سلێمانی")
                        .build())
                .kmrContent(ProjectContentBlock.builder()
                        .title("Parastina hunera xetatiya kurdî")
                        .description("""
                            <p>Projeyek ji bo tomarkirin û fêrkirina hunera xetatiya kurdî ji bo nifşê nû.</p>
                            <img src=\"%s\" alt=\"Xetatî\">
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_CALLIGRAPHY, VID_SINTEL))
                        .location("Silêmanî")
                        .build())
                .projectTypeCkb("هونەر")
                .projectTypeKmr("Huner")
                .status(ProjectStatus.COMPLETED)
                .projectDate(LocalDate.now().minusMonths(14))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of(tagEducation, tagFolklore)))
                .tagsKmr(new LinkedHashSet<>(Set.of(tagEducation, tagFolklore)))
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwCulture, kwHeritage)))
                .keywordsKmr(new LinkedHashSet<>(Set.of(kwCulture, kwHeritage)))
                .build();

        Project p8 = Project.builder()
                .coverUrl(IMG_MOUNTAINS_KRD)
                .coverMediaType(MediaKind.IMAGE)
                .ckbContent(ProjectContentBlock.builder()
                        .title("ئەتڵەسی ناوە جوگرافیە کوردیەکان")
                        .description("""
                            <h2>پرۆژەی جوگرافی</h2>
                            <p>پرۆژەیەک بۆ تۆمارکردنی هەموو ناوە جوگرافیە کوردیەکان و مێژووی ناوەکان.</p>
                            <img src=\"%s\" alt=\"چیا\">
                            <a href=\"%s\">ئەتڵەسی سەرەتایی (PDF)</a>
                            """.formatted(IMG_MOUNTAINS_KRD, PDF_DUMMY))
                        .location("هەرێمی کوردستان")
                        .build())
                .kmrContent(ProjectContentBlock.builder()
                        .title("Atlasa navên erdnîgarî yên kurdî")
                        .description("""
                            <h2>Projeya erdnîgarî</h2>
                            <p>Projeyek ji bo tomarkirina hemû navên erdnîgarî yên kurdî û dîroka navan.</p>
                            <img src=\"%s\" alt=\"Çiya\">
                            <a href=\"%s\">Atlasa destpêkê (PDF)</a>
                            """.formatted(IMG_MOUNTAINS_KRD, PDF_DUMMY))
                        .location("Herêma Kurdistanê")
                        .build())
                .projectTypeCkb("توێژینەوە")
                .projectTypeKmr("Lêkolîn")
                .status(ProjectStatus.ONGOING)
                .projectDate(LocalDate.now().minusMonths(1))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of(tagHistory, tagLanguage)))
                .tagsKmr(new LinkedHashSet<>(Set.of(tagHistory, tagLanguage)))
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwResearch, kwKurdish)))
                .keywordsKmr(new LinkedHashSet<>(Set.of(kwResearch, kwKurdish)))
                .build();

        Project p9 = Project.builder()
                .coverUrl(IMG_TEAM)
                .coverMediaType(MediaKind.IMAGE)
                .ckbContent(ProjectContentBlock.builder()
                        .title("بەرنامەی مامۆستایانی نووسینی گەنج")
                        .description("""
                            <p>پرۆژەیەکی پەروەردەیی بۆ ڕاهێنانی نەوەیەکی نوێ لە نووسەران و توێژەرانی کورد.</p>
                            <img src=\"%s\" alt=\"تیم\">
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_TEAM, VID_FOR_BIGGER))
                        .location("هەولێر")
                        .build())
                .kmrContent(ProjectContentBlock.builder()
                        .title("Bernameya rahênana nivîskarên ciwan")
                        .description("""
                            <p>Projeyeke perwerdeyî ji bo rahênana nifşekî nû ji nivîskar û lêkolînerên kurd.</p>
                            <img src=\"%s\" alt=\"Tîm\">
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_TEAM, VID_FOR_BIGGER))
                        .location("Hewlêr")
                        .build())
                .projectTypeCkb("پەروەردە")
                .projectTypeKmr("Perwerde")
                .status(ProjectStatus.ONGOING)
                .projectDate(LocalDate.now().minusMonths(5))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of(tagEducation)))
                .tagsKmr(new LinkedHashSet<>(Set.of(tagEducation)))
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwResearch)))
                .keywordsKmr(new LinkedHashSet<>(Set.of(kwResearch)))
                .build();

        Project p10 = Project.builder()
                .coverUrl(IMG_ERBIL_CITADEL)
                .coverMediaType(MediaKind.IMAGE)
                .ckbContent(ProjectContentBlock.builder()
                        .title("بەڵگەنامەکردنی شوێنە مێژووییەکانی کوردستان")
                        .description("""
                            <h2>پرۆژەی بەڵگەنامەیی</h2>
                            <p>پرۆژەیەک بۆ وێنەگرتن و بەڵگەنامەکردنی شوێنە مێژووییەکانی کوردستان وەکو قەڵاکان و پردە کۆنەکان.</p>
                            <img src=\"%s\" alt=\"قەڵا\">
                            <video controls src=\"%s\"></video>
                            <p>تا ئێستا ٤٠ شوێنی مێژوویی بەڵگەنامە کراون.</p>
                            """.formatted(IMG_ERBIL_CITADEL, VID_BIG_BUCK_BUNNY))
                        .location("هەرێمی کوردستان")
                        .build())
                .kmrContent(ProjectContentBlock.builder()
                        .title("Belgekirina cihên dîrokî yên Kurdistanê")
                        .description("""
                            <h2>Projeya belgeyî</h2>
                            <p>Projeyek ji bo wênekirin û belgekirina cihên dîrokî yên Kurdistanê wek kela û pirên kevn.</p>
                            <img src=\"%s\" alt=\"Kela\">
                            <video controls src=\"%s\"></video>
                            <p>Heta niha 40 cihên dîrokî hatine belgekirin.</p>
                            """.formatted(IMG_ERBIL_CITADEL, VID_BIG_BUCK_BUNNY))
                        .location("Herêma Kurdistanê")
                        .build())
                .projectTypeCkb("بەڵگەنامە")
                .projectTypeKmr("Belgekirin")
                .status(ProjectStatus.ONGOING)
                .projectDate(LocalDate.now().minusMonths(7))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of(tagHistory, tagArchive)))
                .tagsKmr(new LinkedHashSet<>(Set.of(tagHistory, tagArchive)))
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwHeritage, kwResearch)))
                .keywordsKmr(new LinkedHashSet<>(Set.of(kwHeritage, kwResearch)))
                .mediaGallery(List.of(
                        MediaItem.builder().url(IMG_ERBIL_CITADEL).kind(MediaKind.IMAGE).sortOrder(0).build(),
                        MediaItem.builder().url(IMG_OLD_DOCUMENT).kind(MediaKind.IMAGE).sortOrder(1).build()
                ))
                .build();

        projectRepository.saveAll(List.of(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10));
        log.info("[seed:Project] inserted 10 rows");
    }

    private ProjectTag ensureTag(String name) {
        return projectTagRepository.findAll().stream()
                .filter(t -> t.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> projectTagRepository.save(ProjectTag.builder().name(name).build()));
    }

    private ProjectKeyword ensureKeyword(String name) {
        return projectKeywordRepository.findAll().stream()
                .filter(k -> k.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> projectKeywordRepository.save(ProjectKeyword.builder().name(name).build()));
    }

    // =========================================================================
    // 6) PUBLISHMENT TOPIC — used by Sound / Video / Image / Writing
    // =========================================================================

    @Transactional
    public void seedTopics() {
        if (publishmentTopicRepository.count() > 0) {
            log.info("[seed:PublishmentTopic] table not empty — skipping");
            return;
        }

        List<PublishmentTopic> topics = List.of(
                topic("SOUND",   "گۆرانی فۆلکلۆری",       "Stranên folklorîk"),
                topic("SOUND",   "هۆنراوەی کوردی",        "Helbesta kurdî"),
                topic("SOUND",   "ئەزانی دینی",           "Mewlûd û dînî"),
                topic("SOUND",   "مۆسیقای کلاسیک",        "Muzîka klasîk"),
                topic("VIDEO",   "بەڵگەنامەی مێژوویی",    "Belgefîlma dîrokî"),
                topic("VIDEO",   "فیلمی ئەدەبی",          "Fîlma edebî"),
                topic("VIDEO",   "کلیپی مۆسیقا",          "Klîpên muzîkê"),
                topic("VIDEO",   "چاوپێکەوتن",            "Hevpeyvîn"),
                topic("IMAGE",   "گەلەری مێژوویی",        "Galeriya dîrokî"),
                topic("IMAGE",   "وێنەی فۆلکلۆری",       "Wêneyên folklorîk"),
                topic("IMAGE",   "ژیانی ڕۆژانە",          "Jiyana rojane"),
                topic("IMAGE",   "سروشتی کوردستان",      "Xwezaya Kurdistanê"),
                topic("WRITING", "شیعر و ئەدەب",          "Helbest û edebiyat"),
                topic("WRITING", "مێژووی کورد",           "Dîroka kurdî"),
                topic("WRITING", "زمانناسی کوردی",        "Zimanzaniya kurdî"),
                topic("WRITING", "ڕۆمان و چیرۆک",         "Roman û çîrok")
        );

        publishmentTopicRepository.saveAll(topics);
        log.info("[seed:PublishmentTopic] inserted {} rows", topics.size());
    }

    private PublishmentTopic topic(String entityType, String nameCkb, String nameKmr) {
        return PublishmentTopic.builder()
                .entityType(entityType)
                .nameCkb(nameCkb)
                .nameKmr(nameKmr)
                .build();
    }

    private PublishmentTopic findTopic(String entityType, String nameCkb) {
        return publishmentTopicRepository.findAll().stream()
                .filter(t -> entityType.equals(t.getEntityType()) && nameCkb.equals(t.getNameCkb()))
                .findFirst()
                .orElse(null);
    }

    // =========================================================================
    // 7) SOUND TRACK  — 10 tracks
    // =========================================================================

    @Transactional
    public void seedSoundTracks() {
        if (soundTrackRepository.count() > 0) {
            log.info("[seed:SoundTrack] table not empty — skipping");
            return;
        }

        // ─── Track 1 — SINGLE folk song ──────────────────────────────────────
        SoundTrack st1 = SoundTrack.builder()
                .ckbCoverUrl(IMG_AUDIO_STUDIO)
                .kmrCoverUrl(IMG_AUDIO_STUDIO)
                .hoverCoverUrl(IMG_CONCERT)
                .soundType("Folk")
                .trackState(TrackState.SINGLE)
                .albumOfMemories(false)
                .topic(findTopic("SOUND", "گۆرانی فۆلکلۆری"))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(SoundTrackContent.builder()
                        .title("گۆرانیی کۆنی کوردی - ئەی ڕەقیب")
                        .description("""
                            <p>گۆرانیی نیشتمانیی کوردستان "ئەی ڕەقیب" یەکێکە لە بەناوبانگترین گۆرانیە کوردیەکان. دەقەکەی دڵڵە یونس داڵا ، شاعیری کورد لە ساڵی ١٩٣٨ نووسیویەتی.</p>
                            <img src=\"%s\" alt=\"ستۆدیۆ\">
                            <audio controls src=\"%s\"></audio>
                            <p><a href=\"%s\">دەقی شیعر (PDF)</a></p>
                            """.formatted(IMG_AUDIO_STUDIO, AUD_SONG_1, PDF_DUMMY))
                        .build())
                .kmrContent(SoundTrackContent.builder()
                        .title("Strana kevn a kurdî - Ey Reqîb")
                        .description("""
                            <p>Strana neteweyî ya Kurdistanê "Ey Reqîb" yek ji navdartirîn stranên kurdî ye. Helbestên wê Dildar Yûnis Daloyê, helbestvanê kurd di sala 1938an de nivîsiye.</p>
                            <img src=\"%s\" alt=\"Stûdyo\">
                            <audio controls src=\"%s\"></audio>
                            <p><a href=\"%s\">Deqê helbestê (PDF)</a></p>
                            """.formatted(IMG_AUDIO_STUDIO, AUD_SONG_1, PDF_DUMMY))
                        .build())
                .locations(new LinkedHashSet<>(Set.of("Erbil Studio")))
                .reader("کۆڕی هونەری کوردستان")
                .directors(new LinkedHashSet<>(Set.of("ئاکار شالی")))
                .terms("سۆرانی")
                .thisProjectOfInstitute(true)
                .keywordsCkb(new LinkedHashSet<>(Set.of("نیشتمانی", "ڕەقیب")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Neteweyî", "Reqîb")))
                .tagsCkb(new LinkedHashSet<>(Set.of("فۆلکلۆر", "نیشتمانی")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Folklor", "Neteweyî")))
                .build();
        SoundTrackFile f1 = SoundTrackFile.builder()
                .fileUrl(AUD_SONG_1)
                .title("ئەی ڕەقیب - وەشانی ستۆدیۆ")
                .fileType(FileType.MP3)
                .publishmentYear(2024)
                .fileFormat("MP3")
                .sizeBytes(4_500_000L)
                .durationSeconds(218L)
                .bitRate("320 kbps")
                .sampleRate("44100 Hz")
                .audioChannel(AudioChannel.STEREO)
                .form("نیشتمانی")
                .genre("Anthem")
                .recordingVenue("Erbil Studio A")
                .build();
        f1.addBrochure(SoundTrackBrochure.builder()
                .imageUrl(IMG_AUDIO_STUDIO)
                .caption("ڕووکاری ئەلبووم")
                .brochureOrder(0)
                .build());
        st1.addFile(f1);
        st1.addAttachment(SoundTrackAttachment.builder()
                .fileUrl(PDF_DUMMY)
                .title("دەقی شیعر - PDF")
                .attachmentType(AttachmentType.PDF)
                .sizeBytes(120_000L)
                .mimeType("application/pdf")
                .attachmentOrder(0)
                .build());

        // ─── Track 2 — MULTI album ──────────────────────────────────────────
        SoundTrack st2 = SoundTrack.builder()
                .ckbCoverUrl(IMG_CONCERT)
                .kmrCoverUrl(IMG_CONCERT)
                .hoverCoverUrl(IMG_FOLK_DANCE)
                .soundType("Music")
                .trackState(TrackState.MULTI)
                .albumOfMemories(true)
                .topic(findTopic("SOUND", "هۆنراوەی کوردی"))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(SoundTrackContent.builder()
                        .title("ئەلبوومی یادگاری - گۆرانیە کۆنە کوردیەکان")
                        .description("""
                            <h2>ئەلبوومی تایبەت</h2>
                            <p>کۆکراوەیەکی تایبەت لە ١٠ گۆرانی کۆنی کوردی کە دەگەڕێنەوە بۆ سەردەمی ١٩٥٠ - ١٩٧٠.</p>
                            <img src=\"%s\" alt=\"ئەلبووم\">
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_CONCERT, VID_ELEPHANTS))
                        .build())
                .kmrContent(SoundTrackContent.builder()
                        .title("Albûma bibîranînê - Stranên kevn ên kurdî")
                        .description("""
                            <h2>Albûma taybet</h2>
                            <p>Berhevokeke taybet ji 10 stranên kevn ên kurdî yên ku digihîjin serdema 1950-1970.</p>
                            <img src=\"%s\" alt=\"Albûm\">
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_CONCERT, VID_ELEPHANTS))
                        .build())
                .locations(new LinkedHashSet<>(Set.of("Sulaymaniyah", "Erbil")))
                .reader("هونەرمەندە کۆنەکان")
                .directors(new LinkedHashSet<>(Set.of("سامان حەسەن", "نەرمین جەزائیری")))
                .terms("سۆرانی و کرمانجی")
                .thisProjectOfInstitute(true)
                .albumName("یادگاری - The Memorial")
                .publishmentYear(2023)
                .cdNumber(1)
                .totalTracks(10)
                .keywordsCkb(new LinkedHashSet<>(Set.of("ئەلبووم", "یادگاری")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Albûm", "Bibîranîn")))
                .tagsCkb(new LinkedHashSet<>(Set.of("کۆن", "موزیک")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Kevn", "Muzîk")))
                .build();

        for (int i = 1; i <= 3; i++) {
            String url = switch (i) {
                case 1 -> AUD_SONG_2;
                case 2 -> AUD_SONG_3;
                default -> AUD_SONG_4;
            };
            SoundTrackFile f = SoundTrackFile.builder()
                    .fileUrl(url)
                    .title("Track " + i + " - گۆرانیی " + i)
                    .fileType(FileType.MP3)
                    .publishmentYear(2023)
                    .fileFormat("MP3")
                    .sizeBytes(5_000_000L + (i * 100_000L))
                    .durationSeconds(180L + (i * 30L))
                    .bitRate("320 kbps")
                    .sampleRate("44100 Hz")
                    .audioChannel(AudioChannel.STEREO)
                    .form("کێشدار")
                    .genre("Folk")
                    .recordingVenue("Studio B, Sulaymaniyah")
                    .build();
            st2.addFile(f);
        }
        st2.addAttachment(SoundTrackAttachment.builder()
                .fileUrl(PDF_BROCHURE)
                .title("بوکلێتی ئەلبووم")
                .attachmentType(AttachmentType.PDF)
                .sizeBytes(800_000L)
                .mimeType("application/pdf")
                .attachmentOrder(0)
                .build());

        // ─── Track 3 — SINGLE religious ─────────────────────────────────────
        SoundTrack st3 = SoundTrack.builder()
                .ckbCoverUrl(IMG_CALLIGRAPHY)
                .soundType("Religious")
                .trackState(TrackState.SINGLE)
                .albumOfMemories(false)
                .topic(findTopic("SOUND", "ئەزانی دینی"))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB)))
                .ckbContent(SoundTrackContent.builder()
                        .title("مەولوود - تەکریمی پێغەمبەر")
                        .description("<p>مەولوودێکی کلاسیکی کوردی بۆ تەکریمی پێغەمبەر محەمەد.</p><audio controls src=\"%s\"></audio>".formatted(AUD_SONG_5))
                        .build())
                .kmrContent(SoundTrackContent.builder().title("").description("").build())
                .reader("مەلا عەلی")
                .terms("سۆرانی")
                .thisProjectOfInstitute(false)
                .keywordsCkb(new LinkedHashSet<>(Set.of("مەولوود", "دین")))
                .tagsCkb(new LinkedHashSet<>(Set.of("ئاینی", "تەکریم")))
                .build();
        SoundTrackFile f3 = SoundTrackFile.builder()
                .fileUrl(AUD_SONG_5)
                .title("مەولوود")
                .fileType(FileType.MP3)
                .fileFormat("MP3")
                .sizeBytes(7_200_000L)
                .durationSeconds(420L)
                .audioChannel(AudioChannel.MONO)
                .form("کێشدار")
                .genre("Religious")
                .build();
        st3.addFile(f3);

        // ─── Track 4 — SINGLE classical ─────────────────────────────────────
        SoundTrack st4 = singleSound(
                IMG_AUDIO_STUDIO, IMG_CONCERT, "Classical",
                findTopic("SOUND", "مۆسیقای کلاسیک"),
                "مۆسیقای کلاسیکی کوردی - مەقامی دەشتی",
                "<p>پارچەیەکی مۆسیقای کلاسیکی کوردی بە مەقامی دەشتی، تۆمارکراو لە ستۆدیۆی هەولێر.</p><audio controls src=\"%s\"></audio>".formatted(AUD_SONG_2),
                "Muzîka klasîk a kurdî - Meqamê Deştî",
                "<p>Parçeyeke muzîka klasîk a kurdî bi meqamê Deştî, li stûdyoya Hewlêrê hatiye tomarkirin.</p><audio controls src=\"%s\"></audio>".formatted(AUD_SONG_2),
                "ئۆرکێسترای کلاسیکی کوردستان", AUD_SONG_2, 2024, 330L, AudioChannel.STEREO,
                "Classical", "کلاسیک", "Klasîk", "مەقام", "Meqam", true);

        // ─── Track 5 — SINGLE folk (Kurmanji) ───────────────────────────────
        SoundTrack st5 = singleSound(
                IMG_FOLK_DANCE, IMG_FOLK_DANCE, "Folk",
                findTopic("SOUND", "گۆرانی فۆلکلۆری"),
                "گۆرانیی فۆلکلۆری بادینی - لاوکێ",
                "<p>گۆرانیێکی فۆلکلۆری بە زاراوەی کرمانجی لە ناوچەی بادینان.</p><audio controls src=\"%s\"></audio>".formatted(AUD_SONG_3),
                "Strana folklorî ya behdînî - Lawikê",
                "<p>Stranek folklorî bi zaravayê kurmancî ji herêma Behdînan.</p><audio controls src=\"%s\"></audio>".formatted(AUD_SONG_3),
                "هونەرمەندی گەلی", AUD_SONG_3, 2022, 240L, AudioChannel.STEREO,
                "Folk", "فۆلکلۆر", "Folklor", "بادینی", "Behdînî", true);

        // ─── Track 6 — SINGLE poetry recitation ─────────────────────────────
        SoundTrack st6 = singleSound(
                IMG_OPEN_BOOK, IMG_BOOKS_SHELF, "Poetry",
                findTopic("SOUND", "هۆنراوەی کوردی"),
                "هۆنراوەخوێندنەوە - شیعری شێرکۆ بێکەس",
                "<p>خوێندنەوەی هۆنراوەیەکی شێرکۆ بێکەس بە دەنگی هونەرمەند.</p><audio controls src=\"%s\"></audio>".formatted(AUD_SONG_4),
                "Helbestxwendin - Helbesta Şêrko Bêkes",
                "<p>Xwendina helbesteke Şêrko Bêkes bi dengê hunermend.</p><audio controls src=\"%s\"></audio>".formatted(AUD_SONG_4),
                "ئاسۆ کەمال", AUD_SONG_4, 2023, 195L, AudioChannel.MONO,
                "Recitation", "شیعر", "Helbest", "شێرکۆ", "Şêrko", true);

        // ─── Track 7 — SINGLE religious ─────────────────────────────────────
        SoundTrack st7 = singleSound(
                IMG_CALLIGRAPHY, IMG_CALLIGRAPHY, "Religious",
                findTopic("SOUND", "ئەزانی دینی"),
                "زیکر و مەولوودی کوردی",
                "<p>زیکرێکی کوردی بە شێوازی سۆفیانە.</p><audio controls src=\"%s\"></audio>".formatted(AUD_SONG_5),
                "",
                "",
                "گرووپی سۆفی", AUD_SONG_5, 2021, 510L, AudioChannel.MONO,
                "Religious", "ئاینی", null, "زیکر", null, false);

        // ─── Track 8 — MULTI album (memories) ───────────────────────────────
        SoundTrack st8 = SoundTrack.builder()
                .ckbCoverUrl(IMG_CONCERT)
                .kmrCoverUrl(IMG_CONCERT)
                .hoverCoverUrl(IMG_AUDIO_STUDIO)
                .soundType("Music")
                .trackState(TrackState.MULTI)
                .albumOfMemories(true)
                .topic(findTopic("SOUND", "مۆسیقای کلاسیک"))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(SoundTrackContent.builder()
                        .title("ئەلبوومی مەقامەکانی کوردی")
                        .description("""
                            <h2>کۆکراوەی مەقام</h2>
                            <p>ئەلبوومێکی تەواو لە مەقامە کلاسیکیە کوردیەکان کە لە لایەن ئۆرکێسترای نیشتمانیەوە پێشکەش کراون.</p>
                            <img src=\"%s\" alt=\"ئەلبووم\">
                            <audio controls src=\"%s\"></audio>
                            """.formatted(IMG_CONCERT, AUD_SONG_1))
                        .build())
                .kmrContent(SoundTrackContent.builder()
                        .title("Albûma meqamên kurdî")
                        .description("""
                            <h2>Berhevoka meqaman</h2>
                            <p>Albûmeke tam ji meqamên klasîk ên kurdî ku ji aliyê orkestra neteweyî ve hatine pêşkêşkirin.</p>
                            <img src=\"%s\" alt=\"Albûm\">
                            <audio controls src=\"%s\"></audio>
                            """.formatted(IMG_CONCERT, AUD_SONG_1))
                        .build())
                .locations(new LinkedHashSet<>(Set.of("Erbil", "Sulaymaniyah")))
                .reader("ئۆرکێسترای نیشتمانی")
                .directors(new LinkedHashSet<>(Set.of("دلێر مەحمود")))
                .terms("سۆرانی")
                .thisProjectOfInstitute(true)
                .albumName("مەقامەکان - The Maqams")
                .publishmentYear(2024)
                .cdNumber(1)
                .totalTracks(8)
                .keywordsCkb(new LinkedHashSet<>(Set.of("مەقام", "کلاسیک")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Meqam", "Klasîk")))
                .tagsCkb(new LinkedHashSet<>(Set.of("ئەلبووم", "کلاسیک")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Albûm", "Klasîk")))
                .build();
        for (int i = 1; i <= 4; i++) {
            String url = switch (i) {
                case 1 -> AUD_SONG_1;
                case 2 -> AUD_SONG_2;
                case 3 -> AUD_SONG_3;
                default -> AUD_SONG_5;
            };
            st8.addFile(SoundTrackFile.builder()
                    .fileUrl(url)
                    .title("مەقامی " + i)
                    .fileType(FileType.MP3)
                    .publishmentYear(2024)
                    .fileFormat("MP3")
                    .sizeBytes(6_000_000L + (i * 120_000L))
                    .durationSeconds(300L + (i * 20L))
                    .bitRate("320 kbps")
                    .sampleRate("48000 Hz")
                    .audioChannel(AudioChannel.STEREO)
                    .form("مەقام")
                    .genre("Classical")
                    .recordingVenue("National Studio, Erbil")
                    .build());
        }

        // ─── Track 9 — SINGLE folk (institute) ──────────────────────────────
        SoundTrack st9 = singleSound(
                IMG_AUDIO_STUDIO, IMG_FOLK_DANCE, "Folk",
                findTopic("SOUND", "گۆرانی فۆلکلۆری"),
                "گۆرانیی هەڵپەڕکێ - دیلان",
                "<p>گۆرانیێکی هەڵپەڕکێ بۆ سەماکانی نەتەوەیی.</p><audio controls src=\"%s\"></audio>".formatted(AUD_SONG_2),
                "Strana govendê - Dîlan",
                "<p>Stranek govendê ji bo dîlanên neteweyî.</p><audio controls src=\"%s\"></audio>".formatted(AUD_SONG_2),
                "گرووپی فۆلکلۆری دهۆک", AUD_SONG_2, 2023, 260L, AudioChannel.STEREO,
                "Folk", "هەڵپەڕکێ", "Govend", "سەما", "Dîlan", true);

        // ─── Track 10 — SINGLE poetry ───────────────────────────────────────
        SoundTrack st10 = singleSound(
                IMG_OPEN_BOOK, IMG_OPEN_BOOK, "Poetry",
                findTopic("SOUND", "هۆنراوەی کوردی"),
                "هۆنراوەی نالی - دیوانی کلاسیک",
                "<p>خوێندنەوەی هۆنراوەیەکی نالی، شاعیری گەورەی کلاسیکی کوردی.</p><audio controls src=\"%s\"></audio>".formatted(AUD_SONG_3),
                "Helbesta Nalî - Dîwana klasîk",
                "<p>Xwendina helbesteke Nalî, helbestvanê mezin ê klasîk ê kurdî.</p><audio controls src=\"%s\"></audio>".formatted(AUD_SONG_3),
                "هێمن مەلا کەریم", AUD_SONG_3, 2022, 220L, AudioChannel.MONO,
                "Recitation", "نالی", "Nalî", "کلاسیک", "Klasîk", true);

        soundTrackRepository.saveAll(List.of(st1, st2, st3, st4, st5, st6, st7, st8, st9, st10));
        log.info("[seed:SoundTrack] inserted 10 rows (with files, brochures, attachments)");
    }

    /**
     * Helper for SINGLE-state sound tracks with exactly one file.
     * Kmr fields may be empty strings / null tag to mirror CKB-only entries.
     */
    private SoundTrack singleSound(String ckbCover, String hoverCover, String soundType,
                                   PublishmentTopic topic,
                                   String titleCkb, String descCkb,
                                   String titleKmr, String descKmr,
                                   String reader, String fileUrl, int year, long durationSec,
                                   AudioChannel channel, String genre,
                                   String tagCkb, String tagKmr,
                                   String kwCkb, String kwKmr,
                                   boolean instituteProject) {
        boolean hasKmr = titleKmr != null && !titleKmr.isEmpty();
        Set<Language> langs = hasKmr
                ? new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR))
                : new LinkedHashSet<>(Set.of(Language.CKB));
        Set<String> tagsKmrSet = (tagKmr != null)
                ? new LinkedHashSet<>(Set.of(tagKmr)) : new LinkedHashSet<>();
        Set<String> kwKmrSet = (kwKmr != null)
                ? new LinkedHashSet<>(Set.of(kwKmr)) : new LinkedHashSet<>();
        SoundTrack st = SoundTrack.builder()
                .ckbCoverUrl(ckbCover)
                .kmrCoverUrl(hasKmr ? ckbCover : null)
                .hoverCoverUrl(hoverCover)
                .soundType(soundType)
                .trackState(TrackState.SINGLE)
                .albumOfMemories(false)
                .topic(topic)
                .contentLanguages(langs)
                .ckbContent(SoundTrackContent.builder().title(titleCkb).description(descCkb).build())
                .kmrContent(SoundTrackContent.builder()
                        .title(hasKmr ? titleKmr : "")
                        .description(hasKmr ? descKmr : "")
                        .build())
                .reader(reader)
                .terms("سۆرانی")
                .thisProjectOfInstitute(instituteProject)
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwCkb)))
                .keywordsKmr(kwKmrSet)
                .tagsCkb(new LinkedHashSet<>(Set.of(tagCkb)))
                .tagsKmr(tagsKmrSet)
                .build();
        st.addFile(SoundTrackFile.builder()
                .fileUrl(fileUrl)
                .title(titleCkb)
                .fileType(FileType.MP3)
                .publishmentYear(year)
                .fileFormat("MP3")
                .sizeBytes(5_000_000L)
                .durationSeconds(durationSec)
                .bitRate("320 kbps")
                .sampleRate("44100 Hz")
                .audioChannel(channel)
                .form("کێشدار")
                .genre(genre)
                .recordingVenue("KHI Studio")
                .build());
        return st;
    }

    // =========================================================================
    // 8) VIDEO  — 10 videos
    // =========================================================================

    @Transactional
    public void seedVideos() {
        if (videoRepository.count() > 0) {
            log.info("[seed:Video] table not empty — skipping");
            return;
        }

        Video v1 = Video.builder()
                .ckbCoverUrl(IMG_STAGE)
                .kmrCoverUrl(IMG_STAGE)
                .hoverCoverUrl(IMG_CONCERT)
                .videoType(VideoType.FILM)
                .albumOfMemories(false)
                .topic(findTopic("VIDEO", "بەڵگەنامەی مێژوویی"))
                .ckbContent(VideoContent.builder()
                        .title("بەڵگەنامەی مێژووی کوردستان")
                        .description("""
                            <h2>دەربارەی فیلم</h2>
                            <p>بەڵگەنامەیەکی ٩٠ خولەکی لەسەر مێژووی کوردستان لە ساڵی ١٩٢٠ تا ئەمڕۆ. ئامادە کراوە لە لایەن گرووپێک لە مێژوونووسە بەناوبانگەکانی کورد.</p>
                            <img src=\"%s\" alt=\"بەڵگەنامە\">
                            <video controls src=\"%s\"></video>
                            <h3>بەشەکانی فیلم</h3>
                            <ul>
                                <li>سەردەمی پێش جەنگی یەکەم</li>
                                <li>پەیماننامەی سێڤر</li>
                                <li>کۆماری مەهاباد ١٩٤٦</li>
                                <li>کۆماری هەرێم ٢٠٠٥</li>
                            </ul>
                            <p><a href=\"%s\">پاشخانی بەڵگەنامە</a></p>
                            """.formatted(IMG_STAGE, VID_BIG_BUCK_BUNNY, PDF_DUMMY))
                        .location("هەولێر")
                        .director("کاژین مۆلود")
                        .producer("دامەزراوەی KHI")
                        .build())
                .kmrContent(VideoContent.builder()
                        .title("Belgefîlma dîroka Kurdistanê")
                        .description("""
                            <h2>Derbarê fîlmê</h2>
                            <p>Belgefîlmek 90 deqîqeyî li ser dîroka Kurdistanê ji sala 1920an heta îro. Ji aliyê komek dîroknasên kurd ên navdar ve hatiye amadekirin.</p>
                            <img src=\"%s\" alt=\"Belgefîlm\">
                            <video controls src=\"%s\"></video>
                            <h3>Beşên fîlmê</h3>
                            <ul>
                                <li>Serdema berî şerê yekem</li>
                                <li>Peymana Sêvrê</li>
                                <li>Komara Mehabadê 1946</li>
                                <li>Herêma Kurdistanê 2005</li>
                            </ul>
                            <p><a href=\"%s\">Paşxana belgefîlmê</a></p>
                            """.formatted(IMG_STAGE, VID_BIG_BUCK_BUNNY, PDF_DUMMY))
                        .location("Hewlêr")
                        .director("Kajîn Mewlûd")
                        .producer("Saziya KHI")
                        .build())
                .sourceUrl(VID_BIG_BUCK_BUNNY)
                .fileFormat("MP4")
                .durationSeconds(5400)
                .publishmentDate(LocalDate.now().minusMonths(6))
                .resolution("1920x1080")
                .fileSizeMb(1250.5)
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of("مێژوو", "بەڵگەنامە")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Dîrok", "Belgefîlm")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("کوردستان", "سەربەخۆیی")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Kurdistan", "Serxwebûn")))
                .build();

        Video v2 = Video.builder()
                .ckbCoverUrl(IMG_FOLK_DANCE)
                .kmrCoverUrl(IMG_FOLK_DANCE)
                .videoType(VideoType.VIDEO_CLIP)
                .albumOfMemories(true)
                .topic(findTopic("VIDEO", "کلیپی مۆسیقا"))
                .ckbContent(VideoContent.builder()
                        .title("ئەلبوومی یاد - کلیپە کۆنەکان")
                        .description("<p>کۆکراوەیەک لە کلیپە کۆنە کوردیەکان لە سەردەمی ١٩٨٠ - ٢٠٠٠.</p><img src=\"%s\">".formatted(IMG_FOLK_DANCE))
                        .location("Multiple")
                        .director("نەرمین جەزائیری")
                        .producer("KHI")
                        .build())
                .kmrContent(VideoContent.builder()
                        .title("Albûma bîranînê - Klîpên kevn")
                        .description("<p>Berhevokek ji klîpên kevn ên kurdî yên serdema 1980-2000.</p><img src=\"%s\">".formatted(IMG_FOLK_DANCE))
                        .location("Cuda")
                        .director("Nermîn Cezairî")
                        .producer("KHI")
                        .build())
                .publishmentDate(LocalDate.now().minusMonths(2))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of("کلیپ", "موزیک")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Klîp", "Muzîk")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("کۆن", "یادگار")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Kevn", "Bîranîn")))
                .build();

        v2.addClipItem(VideoClipItem.builder()
                .url(VID_FOR_BIGGER)
                .clipNumber(1)
                .durationSeconds(180)
                .resolution("1280x720")
                .fileFormat("MP4")
                .fileSizeMb(15.4)
                .titleCkb("کلیپی یەکەم - گۆرانیی نەرمین")
                .titleKmr("Klîpa yekem - Strana Nermîn")
                .descriptionCkb("<p>یەکەمین کلیپ لە ئەلبووم.</p><img src=\"%s\">".formatted(IMG_CONCERT))
                .descriptionKmr("<p>Klîpa yekem ji albûmê.</p><img src=\"%s\">".formatted(IMG_CONCERT))
                .build());
        v2.addClipItem(VideoClipItem.builder()
                .url(VID_ELEPHANTS)
                .clipNumber(2)
                .durationSeconds(220)
                .resolution("1280x720")
                .fileFormat("MP4")
                .fileSizeMb(18.7)
                .titleCkb("کلیپی دووەم - بەراندوو")
                .titleKmr("Klîpa duyem - Berando")
                .descriptionCkb("<p>کلیپێکی فۆلکلۆری.</p>")
                .descriptionKmr("<p>Klîpek folklorîk.</p>")
                .build());
        v2.addClipItem(VideoClipItem.builder()
                .url(VID_SINTEL)
                .clipNumber(3)
                .durationSeconds(195)
                .resolution("1920x1080")
                .fileFormat("MP4")
                .fileSizeMb(22.1)
                .titleCkb("کلیپی سێیەم - گۆرانیی شار")
                .titleKmr("Klîpa sêyem - Strana bajêr")
                .descriptionCkb("<p>کلیپێکی هاوچەرخ لە سلێمانی.</p>")
                .descriptionKmr("<p>Klîpek hevçerx li Silêmanî.</p>")
                .build());

        Video v3 = Video.builder()
                .ckbCoverUrl(IMG_INTERVIEW)
                .kmrCoverUrl(IMG_INTERVIEW)
                .videoType(VideoType.FILM)
                .albumOfMemories(false)
                .topic(findTopic("VIDEO", "فیلمی ئەدەبی"))
                .ckbContent(VideoContent.builder()
                        .title("چاوپێکەوتنێک لەگەڵ شاعیر شێرکۆ بێکەس")
                        .description("""
                            <p>چاوپێکەوتنێکی فراوان لەگەڵ شاعیر شێرکۆ بێکەس لە یەکێک لە دوا کاتژمێرە لە ژیانیدا.</p>
                            <img src=\"%s\" alt=\"چاوپێکەوتن\">
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_INTERVIEW, VID_SINTEL))
                        .location("سلێمانی")
                        .director("ئاشتی هیرشی")
                        .producer("KHI Documentary")
                        .build())
                .kmrContent(VideoContent.builder()
                        .title("Hevpeyvîn bi helbestvan Şêrko Bêkes re")
                        .description("""
                            <p>Hevpeyvîneke berfireh bi helbestvan Şêrko Bêkes re di yek ji saetên dawî yên jiyana wî de.</p>
                            <img src=\"%s\" alt=\"Hevpeyvîn\">
                            <video controls src=\"%s\"></video>
                            """.formatted(IMG_INTERVIEW, VID_SINTEL))
                        .location("Silêmanî")
                        .director("Aştî Hîrşî")
                        .producer("KHI Documentary")
                        .build())
                .sourceUrl(VID_SINTEL)
                .fileFormat("MP4")
                .durationSeconds(3600)
                .publishmentDate(LocalDate.now().minusYears(2))
                .resolution("1920x1080")
                .fileSizeMb(850.0)
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of("شاعیر", "چاوپێکەوتن")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Helbestvan", "Hevpeyvîn")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("شێرکۆ", "ئەدەب")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Şêrko", "Edebiyat")))
                .build();

        Video v4 = filmVideo(IMG_ERBIL_CITADEL, IMG_MOUNTAINS_KRD,
                findTopic("VIDEO", "بەڵگەنامەی مێژوویی"),
                "بەڵگەنامەی قەڵای هەولێر",
                """
                    <h2>کۆنترین شار</h2>
                    <p>بەڵگەنامەیەک لەسەر قەڵای هەولێر، یەکێک لە کۆنترین شارە دانیشتراوەکانی جیهان.</p>
                    <img src=\"%s\" alt=\"قەڵا\"><video controls src=\"%s\"></video>
                    """.formatted(IMG_ERBIL_CITADEL, VID_ELEPHANTS),
                "Belgefîlma Kelaya Hewlêrê",
                """
                    <h2>Kevintirîn bajar</h2>
                    <p>Belgefîlmek li ser Kelaya Hewlêrê, yek ji kevintirîn bajarên niştecîh ên cîhanê.</p>
                    <img src=\"%s\" alt=\"Kela\"><video controls src=\"%s\"></video>
                    """.formatted(IMG_ERBIL_CITADEL, VID_ELEPHANTS),
                "هەولێر", "Hewlêr", "ڕێبوار ئەحمەد", "Rêbwar Ehmed",
                VID_ELEPHANTS, 2700, "1920x1080", 620.0,
                LocalDate.now().minusMonths(9),
                "قەڵا", "Kela", "مێژوو", "Dîrok");

        Video v5 = filmVideo(IMG_FOLK_DANCE, IMG_TRADITIONAL,
                findTopic("VIDEO", "بەڵگەنامەی مێژوویی"),
                "بەڵگەنامەی فۆلکلۆری کوردی",
                """
                    <p>بەڵگەنامەیەک لەسەر سەما و جلوبەرگ و دابونەریتە کوردیەکان.</p>
                    <img src=\"%s\"><video controls src=\"%s\"></video>
                    """.formatted(IMG_FOLK_DANCE, VID_TEARS),
                "Belgefîlma folklora kurdî",
                """
                    <p>Belgefîlmek li ser govend, kinc û edet û toreyên kurdî.</p>
                    <img src=\"%s\"><video controls src=\"%s\"></video>
                    """.formatted(IMG_FOLK_DANCE, VID_TEARS),
                "دهۆک", "Duhok", "شیلان عومەر", "Şîlan Omer",
                VID_TEARS, 3300, "1920x1080", 740.0,
                LocalDate.now().minusMonths(11),
                "فۆلکلۆر", "Folklor", "کلتوور", "Çand");

        Video v6 = filmVideo(IMG_INTERVIEW, IMG_PRESENTER,
                findTopic("VIDEO", "چاوپێکەوتن"),
                "چاوپێکەوتن لەگەڵ هونەرمەندی گەلی",
                "<p>چاوپێکەوتنێک لەگەڵ یەکێک لە هونەرمەندە گەلیە ناودارەکان.</p><video controls src=\"%s\"></video>".formatted(VID_FOR_BIGGER),
                "Hevpeyvîn bi hunermendê gelî re",
                "<p>Hevpeyvînek bi yek ji hunermendên gelî yên navdar re.</p><video controls src=\"%s\"></video>".formatted(VID_FOR_BIGGER),
                "سلێمانی", "Silêmanî", "کارزان سەعید", "Karzan Seîd",
                VID_FOR_BIGGER, 1800, "1280x720", 210.0,
                LocalDate.now().minusMonths(3),
                "چاوپێکەوتن", "Hevpeyvîn", "هونەرمەند", "Hunermend");

        Video v7 = filmVideo(IMG_OPEN_BOOK, IMG_BOOKS_SHELF,
                findTopic("VIDEO", "فیلمی ئەدەبی"),
                "فیلمێکی کورت لەسەر ئەدەبی کوردی",
                "<p>فیلمێکی کورتی ئەدەبی لەسەر ژیانی نووسەرێکی کورد.</p><video controls src=\"%s\"></video>".formatted(VID_SINTEL),
                "Fîlmek kurt li ser edebiyata kurdî",
                "<p>Fîlmeke kurt a edebî li ser jiyana nivîskarekî kurd.</p><video controls src=\"%s\"></video>".formatted(VID_SINTEL),
                "هەولێر", "Hewlêr", "هانا ڕەسوڵ", "Hana Resûl",
                VID_SINTEL, 1500, "1920x1080", 320.0,
                LocalDate.now().minusMonths(4),
                "ئەدەب", "Edebiyat", "فیلم", "Fîlm");

        // ─── v8 — VIDEO_CLIP album with clips ───────────────────────────────
        Video v8 = Video.builder()
                .ckbCoverUrl(IMG_CONCERT)
                .kmrCoverUrl(IMG_CONCERT)
                .videoType(VideoType.VIDEO_CLIP)
                .albumOfMemories(true)
                .topic(findTopic("VIDEO", "کلیپی مۆسیقا"))
                .ckbContent(VideoContent.builder()
                        .title("کۆکراوەی کلیپی مۆسیقای هاوچەرخ")
                        .description("<p>کۆکراوەیەک لە کلیپە مۆسیقیە هاوچەرخەکانی کوردی.</p><img src=\"%s\">".formatted(IMG_CONCERT))
                        .location("Multiple")
                        .director("دیار سابیر")
                        .producer("KHI")
                        .build())
                .kmrContent(VideoContent.builder()
                        .title("Berhevoka klîpên muzîka hevçerx")
                        .description("<p>Berhevokek ji klîpên muzîka hevçerx ên kurdî.</p><img src=\"%s\">".formatted(IMG_CONCERT))
                        .location("Cuda")
                        .director("Diyar Sabir")
                        .producer("KHI")
                        .build())
                .publishmentDate(LocalDate.now().minusMonths(1))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of("کلیپ", "هاوچەرخ")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Klîp", "Hevçerx")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("مۆسیقا")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Muzîk")))
                .build();
        v8.addClipItem(VideoClipItem.builder()
                .url(VID_BIG_BUCK_BUNNY).clipNumber(1).durationSeconds(200)
                .resolution("1920x1080").fileFormat("MP4").fileSizeMb(20.0)
                .titleCkb("کلیپی یەکەم").titleKmr("Klîpa yekem")
                .descriptionCkb("<p>کلیپی یەکەم.</p>").descriptionKmr("<p>Klîpa yekem.</p>")
                .build());
        v8.addClipItem(VideoClipItem.builder()
                .url(VID_FOR_BIGGER).clipNumber(2).durationSeconds(165)
                .resolution("1280x720").fileFormat("MP4").fileSizeMb(14.0)
                .titleCkb("کلیپی دووەم").titleKmr("Klîpa duyem")
                .descriptionCkb("<p>کلیپی دووەم.</p>").descriptionKmr("<p>Klîpa duyem.</p>")
                .build());

        Video v9 = filmVideo(IMG_AUDIO_STUDIO, IMG_STAGE,
                findTopic("VIDEO", "بەڵگەنامەی مێژوویی"),
                "بەڵگەنامەی مۆسیقای کوردی",
                "<p>بەڵگەنامەیەک لەسەر مێژووی مۆسیقای کوردی و ئامێرە مۆسیقیە کۆنەکان.</p><video controls src=\"%s\"></video>".formatted(VID_BIG_BUCK_BUNNY),
                "Belgefîlma muzîka kurdî",
                "<p>Belgefîlmek li ser dîroka muzîka kurdî û amûrên muzîkê yên kevn.</p><video controls src=\"%s\"></video>".formatted(VID_BIG_BUCK_BUNNY),
                "سلێمانی", "Silêmanî", "ئاسۆ مەجید", "Aso Mecîd",
                VID_BIG_BUCK_BUNNY, 2400, "1920x1080", 560.0,
                LocalDate.now().minusMonths(8),
                "مۆسیقا", "Muzîk", "مێژوو", "Dîrok");

        Video v10 = filmVideo(IMG_MOUNTAINS_KRD, IMG_ERBIL_CITADEL,
                findTopic("VIDEO", "بەڵگەنامەی مێژوویی"),
                "بەڵگەنامەی سروشتی کوردستان",
                """
                    <p>بەڵگەنامەیەک لەسەر جوانی سروشتی کوردستان، چیاکان و دۆڵەکان و ڕووبارەکان.</p>
                    <img src=\"%s\"><video controls src=\"%s\"></video>
                    """.formatted(IMG_MOUNTAINS_KRD, VID_TEARS),
                "Belgefîlma xwezaya Kurdistanê",
                """
                    <p>Belgefîlmek li ser bedewiya xwezaya Kurdistanê, çiya û newal û çeman.</p>
                    <img src=\"%s\"><video controls src=\"%s\"></video>
                    """.formatted(IMG_MOUNTAINS_KRD, VID_TEARS),
                "هەرێمی کوردستان", "Herêma Kurdistanê", "ژیار نەوزاد", "Jiyar Newzad",
                VID_TEARS, 3000, "3840x2160", 1800.0,
                LocalDate.now().minusMonths(2),
                "سروشت", "Xweza", "چیا", "Çiya");

        videoRepository.saveAll(List.of(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10));
        log.info("[seed:Video] inserted 10 rows (FILM + VIDEO_CLIP mix)");
    }

    /** Helper for single-source FILM videos (no clip items). */
    private Video filmVideo(String ckbCover, String hoverCover, PublishmentTopic topic,
                            String titleCkb, String descCkb,
                            String titleKmr, String descKmr,
                            String locCkb, String locKmr, String directorCkb, String directorKmr,
                            String sourceUrl, int durationSec, String resolution, double sizeMb,
                            LocalDate published,
                            String tagCkb, String tagKmr, String kwCkb, String kwKmr) {
        return Video.builder()
                .ckbCoverUrl(ckbCover)
                .kmrCoverUrl(ckbCover)
                .hoverCoverUrl(hoverCover)
                .videoType(VideoType.FILM)
                .albumOfMemories(false)
                .topic(topic)
                .ckbContent(VideoContent.builder()
                        .title(titleCkb).description(descCkb)
                        .location(locCkb).director(directorCkb).producer("دامەزراوەی KHI").build())
                .kmrContent(VideoContent.builder()
                        .title(titleKmr).description(descKmr)
                        .location(locKmr).director(directorKmr).producer("Saziya KHI").build())
                .sourceUrl(sourceUrl)
                .fileFormat("MP4")
                .durationSeconds(durationSec)
                .publishmentDate(published)
                .resolution(resolution)
                .fileSizeMb(sizeMb)
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of(tagCkb)))
                .tagsKmr(new LinkedHashSet<>(Set.of(tagKmr)))
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwCkb)))
                .keywordsKmr(new LinkedHashSet<>(Set.of(kwKmr)))
                .build();
    }

    // =========================================================================
    // 9) IMAGE COLLECTION  — 10 collections
    // =========================================================================

    @Transactional
    public void seedImageCollections() {
        if (imageCollectionRepository.count() > 0) {
            log.info("[seed:ImageCollection] table not empty — skipping");
            return;
        }

        // ─── ic1 — GALLERY (historical) ─────────────────────────────────────
        ImageCollection ic1 = ImageCollection.builder()
                .collectionType(ImageCollectionType.GALLERY)
                .ckbCoverUrl(IMG_ERBIL_CITADEL)
                .kmrCoverUrl(IMG_ERBIL_CITADEL)
                .hoverCoverUrl(IMG_OLD_DOCUMENT)
                .topic(findTopic("IMAGE", "گەلەری مێژوویی"))
                .ckbContent(ImageContent.builder()
                        .title("گەلەری وێنە مێژووییەکانی هەولێر")
                        .description("""
                            <h2>وێنە کۆنەکان</h2>
                            <p>کۆکراوەیەک لە وێنە مێژووییەکانی شاری هەولێر لە سەردەمی ١٩٢٠ - ١٩٦٠.</p>
                            <img src=\"%s\" alt=\"هەولێری کۆن\">
                            """.formatted(IMG_ERBIL_CITADEL))
                        .location("هەولێر")
                        .collectedBy("ئەرشیفی نەتەوەیی")
                        .build())
                .kmrContent(ImageContent.builder()
                        .title("Galeriya wêneyên dîrokî yên Hewlêrê")
                        .description("""
                            <h2>Wêneyên kevn</h2>
                            <p>Berhevokek ji wêneyên dîrokî yên bajarê Hewlêrê ji serdema 1920-1960.</p>
                            <img src=\"%s\" alt=\"Hewlêra kevn\">
                            """.formatted(IMG_ERBIL_CITADEL))
                        .location("Hewlêr")
                        .collectedBy("Arşîva neteweyî")
                        .build())
                .publishmentDate(LocalDate.now().minusMonths(5))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of("مێژوو", "هەولێر")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Dîrok", "Hewlêr")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("کۆن", "ئەرشیف")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Kevn", "Arşîv")))
                .build();
        addAlbumItem(ic1, IMG_ERBIL_CITADEL, "قەڵای هەولێر ١٩٣٠", "Kelaya Hewlêrê 1930", 0);
        addAlbumItem(ic1, IMG_OLD_DOCUMENT, "دۆکیومێنتی کۆن", "Belgenameya kevn", 1);
        addAlbumItem(ic1, IMG_ARCHIVE, "ئەرشیفی شار", "Arşîva bajêr", 2);

        // ─── ic2 — PHOTO_STORY (folklore) ───────────────────────────────────
        ImageCollection ic2 = ImageCollection.builder()
                .collectionType(ImageCollectionType.PHOTO_STORY)
                .ckbCoverUrl(IMG_FOLK_DANCE)
                .kmrCoverUrl(IMG_FOLK_DANCE)
                .hoverCoverUrl(IMG_TRADITIONAL)
                .topic(findTopic("IMAGE", "وێنەی فۆلکلۆری"))
                .ckbContent(ImageContent.builder()
                        .title("چیرۆکی وێنەیی - جلوبەرگی نەتەوەیی کوردی")
                        .description("""
                            <p>چیرۆکێکی وێنەیی لەسەر جۆراوجۆری جلوبەرگی نەتەوەیی کوردی لە ناوچە جیاوازەکان.</p>
                            <img src=\"%s\" alt=\"جلوبەرگ\">
                            """.formatted(IMG_TRADITIONAL))
                        .location("هەرێمی کوردستان")
                        .collectedBy("تیمی فۆلکلۆر")
                        .build())
                .kmrContent(ImageContent.builder()
                        .title("Çîroka wêneyî - Kincê neteweyî yê kurdî")
                        .description("""
                            <p>Çîrokeke wêneyî li ser cûrbecûriya kincê neteweyî yê kurdî li herêmên cuda.</p>
                            <img src=\"%s\" alt=\"Kinc\">
                            """.formatted(IMG_TRADITIONAL))
                        .location("Herêma Kurdistanê")
                        .collectedBy("Tîma folklorê")
                        .build())
                .publishmentDate(LocalDate.now().minusMonths(3))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of("فۆلکلۆر", "جلوبەرگ")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Folklor", "Kinc")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("نەتەوەیی", "کلتوور")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Neteweyî", "Çand")))
                .build();
        addAlbumItem(ic2, IMG_TRADITIONAL, "جلوبەرگی بادینی", "Kincê behdînî", 0);
        addAlbumItem(ic2, IMG_FOLK_DANCE, "جلوبەرگی سۆرانی", "Kincê soranî", 1);

        // ─── ic3 — SINGLE ───────────────────────────────────────────────────
        ImageCollection ic3 = ImageCollection.builder()
                .collectionType(ImageCollectionType.SINGLE)
                .ckbCoverUrl(IMG_MOUNTAINS_KRD)
                .topic(findTopic("IMAGE", "سروشتی کوردستان"))
                .ckbContent(ImageContent.builder()
                        .title("وێنەیەک لە چیاکانی کوردستان")
                        .description("<p>وێنەیەکی تاک لە جوانی چیاکانی کوردستان.</p><img src=\"%s\">".formatted(IMG_MOUNTAINS_KRD))
                        .location("کوردستان")
                        .collectedBy("KHI")
                        .build())
                .kmrContent(ImageContent.builder().title("").description("").build())
                .publishmentDate(LocalDate.now().minusMonths(1))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB)))
                .tagsCkb(new LinkedHashSet<>(Set.of("سروشت")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("چیا")))
                .build();
        addAlbumItem(ic3, IMG_MOUNTAINS_KRD, "چیاکانی کوردستان", null, 0);

        // ─── ic4 — GALLERY (daily life) ─────────────────────────────────────
        ImageCollection ic4 = gallery(ImageCollectionType.GALLERY,
                IMG_TRADITIONAL, IMG_FOLK_DANCE,
                findTopic("IMAGE", "ژیانی ڕۆژانە"),
                "ژیانی ڕۆژانە لە گوندەکانی کوردستان",
                "<p>کۆکراوەیەک لە وێنەی ژیانی ڕۆژانە لە گوندە کوردیەکان.</p><img src=\"%s\">".formatted(IMG_TRADITIONAL),
                "Jiyana rojane li gundên Kurdistanê",
                "<p>Berhevokek ji wêneyên jiyana rojane li gundên kurdî.</p><img src=\"%s\">".formatted(IMG_TRADITIONAL),
                "گوندەکانی کوردستان", "Gundên Kurdistanê", "تیمی وێنەگری", "Tîma wênegiriyê",
                LocalDate.now().minusMonths(6), "ژیان", "Jiyan", "گوند", "Gund");
        addAlbumItem(ic4, IMG_TRADITIONAL, "گوندێکی کوردی", "Gundekî kurdî", 0);
        addAlbumItem(ic4, IMG_MOUNTAINS_KRD, "کشتوکاڵ لە چیا", "Cotkarî li çiya", 1);
        addAlbumItem(ic4, IMG_FOLK_DANCE, "ئاهەنگی گوند", "Şahiya gund", 2);

        // ─── ic5 — GALLERY (nature) ─────────────────────────────────────────
        ImageCollection ic5 = gallery(ImageCollectionType.GALLERY,
                IMG_MOUNTAINS_KRD, IMG_ERBIL_CITADEL,
                findTopic("IMAGE", "سروشتی کوردستان"),
                "گەلەری سروشتی کوردستان",
                "<p>وێنەی جوانی سروشتی کوردستان لە وەرزە جیاوازەکان.</p><img src=\"%s\">".formatted(IMG_MOUNTAINS_KRD),
                "Galeriya xwezaya Kurdistanê",
                "<p>Wêneyên bedewiya xwezaya Kurdistanê di demsalên cuda de.</p><img src=\"%s\">".formatted(IMG_MOUNTAINS_KRD),
                "هەرێمی کوردستان", "Herêma Kurdistanê", "ئاکۆ ڕەشید", "Ako Reşîd",
                LocalDate.now().minusMonths(4), "سروشت", "Xweza", "چیا", "Çiya");
        addAlbumItem(ic5, IMG_MOUNTAINS_KRD, "چیای زۆزان", "Çiyayê zozan", 0);
        addAlbumItem(ic5, IMG_ERBIL_CITADEL, "دیمەنی شار", "Dîmena bajêr", 1);

        // ─── ic6 — PHOTO_STORY (history) ────────────────────────────────────
        ImageCollection ic6 = gallery(ImageCollectionType.PHOTO_STORY,
                IMG_OLD_DOCUMENT, IMG_ARCHIVE,
                findTopic("IMAGE", "گەلەری مێژوویی"),
                "چیرۆکی وێنەیی - دۆکیومێنتە مێژووییەکان",
                "<p>چیرۆکێکی وێنەیی لەسەر دۆکیومێنتە مێژووییە کۆنەکانی کوردستان.</p><img src=\"%s\">".formatted(IMG_OLD_DOCUMENT),
                "Çîroka wêneyî - Belgenameyên dîrokî",
                "<p>Çîrokeke wêneyî li ser belgenameyên dîrokî yên kevn ên Kurdistanê.</p><img src=\"%s\">".formatted(IMG_OLD_DOCUMENT),
                "هەولێر", "Hewlêr", "ئەرشیفی نەتەوەیی", "Arşîva neteweyî",
                LocalDate.now().minusMonths(7), "مێژوو", "Dîrok", "دۆکیومێنت", "Belge");
        addAlbumItem(ic6, IMG_OLD_DOCUMENT, "دۆکیومێنتی عوسمانی", "Belgeya osmanî", 0);
        addAlbumItem(ic6, IMG_ARCHIVE, "ئەرشیفی کۆن", "Arşîva kevn", 1);
        addAlbumItem(ic6, IMG_CALLIGRAPHY, "خوسنۆڤی کۆن", "Xetatiya kevn", 2);

        // ─── ic7 — GALLERY (folklore) ───────────────────────────────────────
        ImageCollection ic7 = gallery(ImageCollectionType.GALLERY,
                IMG_FOLK_DANCE, IMG_CONCERT,
                findTopic("IMAGE", "وێنەی فۆلکلۆری"),
                "گەلەری سەماکانی کوردی",
                "<p>کۆکراوەیەک لە وێنەی سەما و هەڵپەڕکێ کوردیەکان.</p><img src=\"%s\">".formatted(IMG_FOLK_DANCE),
                "Galeriya govendên kurdî",
                "<p>Berhevokek ji wêneyên govend û dîlanên kurdî.</p><img src=\"%s\">".formatted(IMG_FOLK_DANCE),
                "دهۆک", "Duhok", "تیمی فۆلکلۆر", "Tîma folklorê",
                LocalDate.now().minusMonths(2), "سەما", "Govend", "فۆلکلۆر", "Folklor");
        addAlbumItem(ic7, IMG_FOLK_DANCE, "هەڵپەڕکێ", "Govend", 0);
        addAlbumItem(ic7, IMG_TRADITIONAL, "جلوبەرگی سەما", "Kincê govendê", 1);

        // ─── ic8 — SINGLE ───────────────────────────────────────────────────
        ImageCollection ic8 = gallery(ImageCollectionType.SINGLE,
                IMG_CALLIGRAPHY, IMG_OPEN_BOOK,
                findTopic("IMAGE", "گەلەری مێژوویی"),
                "نموونەیەک لە خوسنۆڤی کوردی",
                "<p>وێنەیەکی تاک لە هونەری خوسنۆڤی کوردی.</p><img src=\"%s\">".formatted(IMG_CALLIGRAPHY),
                "Nimûneyek ji xetatiya kurdî",
                "<p>Wêneyek tek ji hunera xetatiya kurdî.</p><img src=\"%s\">".formatted(IMG_CALLIGRAPHY),
                "سلێمانی", "Silêmanî", "KHI", "KHI",
                LocalDate.now().minusMonths(1), "خوسنۆڤی", "Xetatî", "هونەر", "Huner");
        addAlbumItem(ic8, IMG_CALLIGRAPHY, "خوسنۆڤی کوردی", "Xetatiya kurdî", 0);

        // ─── ic9 — GALLERY (daily life) ─────────────────────────────────────
        ImageCollection ic9 = gallery(ImageCollectionType.GALLERY,
                IMG_LIBRARY, IMG_BOOKS_SHELF,
                findTopic("IMAGE", "ژیانی ڕۆژانە"),
                "گەلەری کتێبخانە و خوێندنگەکان",
                "<p>وێنەی کتێبخانە و ناوەندە فێرکارییەکانی کوردستان.</p><img src=\"%s\">".formatted(IMG_LIBRARY),
                "Galeriya pirtûkxane û dibistanan",
                "<p>Wêneyên pirtûkxane û navendên fêrkariyê yên Kurdistanê.</p><img src=\"%s\">".formatted(IMG_LIBRARY),
                "هەولێر", "Hewlêr", "تیمی KHI", "Tîma KHI",
                LocalDate.now().minusMonths(3), "کتێبخانە", "Pirtûkxane", "خوێندن", "Xwendin");
        addAlbumItem(ic9, IMG_LIBRARY, "کتێبخانەی نەتەوەیی", "Pirtûkxaneya neteweyî", 0);
        addAlbumItem(ic9, IMG_BOOKS_SHELF, "ڕەفی کتێبەکان", "Refê pirtûkan", 1);
        addAlbumItem(ic9, IMG_OPEN_BOOK, "کتێبێکی کراوە", "Pirtûkeke vekirî", 2);

        // ─── ic10 — PHOTO_STORY (nature) ────────────────────────────────────
        ImageCollection ic10 = gallery(ImageCollectionType.PHOTO_STORY,
                IMG_MOUNTAINS_KRD, IMG_TRADITIONAL,
                findTopic("IMAGE", "سروشتی کوردستان"),
                "چیرۆکی وێنەیی - وەرزەکانی کوردستان",
                "<p>چیرۆکێکی وێنەیی لەسەر گۆڕانی وەرزەکان لە کوردستان.</p><img src=\"%s\">".formatted(IMG_MOUNTAINS_KRD),
                "Çîroka wêneyî - Demsalên Kurdistanê",
                "<p>Çîrokeke wêneyî li ser guherîna demsalan li Kurdistanê.</p><img src=\"%s\">".formatted(IMG_MOUNTAINS_KRD),
                "هەرێمی کوردستان", "Herêma Kurdistanê", "ئاکۆ ڕەشید", "Ako Reşîd",
                LocalDate.now().minusMonths(5), "وەرز", "Demsal", "سروشت", "Xweza");
        addAlbumItem(ic10, IMG_MOUNTAINS_KRD, "بەهاری کوردستان", "Bihara Kurdistanê", 0);
        addAlbumItem(ic10, IMG_ERBIL_CITADEL, "زستانی شار", "Zivistana bajêr", 1);

        imageCollectionRepository.saveAll(List.of(ic1, ic2, ic3, ic4, ic5, ic6, ic7, ic8, ic9, ic10));
        log.info("[seed:ImageCollection] inserted 10 rows (GALLERY / PHOTO_STORY / SINGLE)");
    }

    /** Adds one image item to a collection's album, mirroring CKB/KMR captions. */
    private void addAlbumItem(ImageCollection collection, String imageUrl,
                              String captionCkb, String captionKmr, int sortOrder) {
        ImageAlbumItem item = ImageAlbumItem.builder()
                .imageUrl(imageUrl)
                .captionCkb(captionCkb)
                .captionKmr(captionKmr)
                .descriptionCkb(captionCkb)
                .descriptionKmr(captionKmr)
                .sortOrder(sortOrder)
                .fileSizeBytes(2_400_000L)
                .widthPx(1600)
                .heightPx(1067)
                .mimeType("image/jpeg")
                .imageCollection(collection)
                .build();
        collection.getImageAlbum().add(item);
    }

    /** Builder helper for image collections (album items added separately). */
    private ImageCollection gallery(ImageCollectionType type, String ckbCover, String hoverCover,
                                    PublishmentTopic topic,
                                    String titleCkb, String descCkb,
                                    String titleKmr, String descKmr,
                                    String locCkb, String locKmr, String collectedByCkb, String collectedByKmr,
                                    LocalDate published,
                                    String tagCkb, String tagKmr, String kwCkb, String kwKmr) {
        return ImageCollection.builder()
                .collectionType(type)
                .ckbCoverUrl(ckbCover)
                .kmrCoverUrl(ckbCover)
                .hoverCoverUrl(hoverCover)
                .topic(topic)
                .ckbContent(ImageContent.builder()
                        .title(titleCkb).description(descCkb)
                        .location(locCkb).collectedBy(collectedByCkb).build())
                .kmrContent(ImageContent.builder()
                        .title(titleKmr).description(descKmr)
                        .location(locKmr).collectedBy(collectedByKmr).build())
                .publishmentDate(published)
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of(tagCkb)))
                .tagsKmr(new LinkedHashSet<>(Set.of(tagKmr)))
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwCkb)))
                .keywordsKmr(new LinkedHashSet<>(Set.of(kwKmr)))
                .build();
    }

    // =========================================================================
    // 10) WRITING  — 10 writings
    // =========================================================================

    @Transactional
    public void seedWritings() {
        if (writingRepository.count() > 0) {
            log.info("[seed:Writing] table not empty — skipping");
            return;
        }

        String seriesId = UUID.randomUUID().toString();

        Writing w1 = Writing.builder()
                .ckbCoverUrl(IMG_BOOKS_SHELF)
                .kmrCoverUrl(IMG_BOOKS_SHELF)
                .hoverCoverUrl(IMG_OPEN_BOOK)
                .topic(findTopic("WRITING", "شیعر و ئەدەب"))
                .bookGenres(new LinkedHashSet<>(Set.of(BookGenre.POETRY)))
                .publishedByInstitute(true)
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(WritingContent.builder()
                        .title("دیوانی شیعری کوردی - بەرگی یەکەم")
                        .description("""
                            <h2>کۆکراوەی شیعر</h2>
                            <p>بەرگی یەکەمی دیوانێکی شیعری کوردی کە کۆمەڵێک لە باشترین شیعرەکانی شاعیرانی کلاسیکی کورد لەخۆ دەگرێت.</p>
                            <img src=\"%s\" alt=\"دیوان\">
                            <p>ئەم بەرگە تایبەتە بە شاعیرانی سەدەی ١٩.</p>
                            """.formatted(IMG_BOOKS_SHELF))
                        .writer("ئامادەکردنی: تیمی ئەدەبی KHI")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(3_500_000L)
                        .pageCount(280)
                        .genre("شیعری کلاسیک")
                        .build())
                .kmrContent(WritingContent.builder()
                        .title("Dîwana helbesta kurdî - Bergê yekem")
                        .description("""
                            <h2>Berhevoka helbestê</h2>
                            <p>Bergê yekem ê dîwaneke helbesta kurdî ku komek ji baştirîn helbestên helbestvanên klasîk ên kurd dihewîne.</p>
                            <img src=\"%s\" alt=\"Dîwan\">
                            <p>Ev berg taybet e bi helbestvanên sedsala 19an.</p>
                            """.formatted(IMG_BOOKS_SHELF))
                        .writer("Amadekirin: Tîma edebî ya KHI")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(3_500_000L)
                        .pageCount(280)
                        .genre("Helbesta klasîk")
                        .build())
                .keywordsCkb(new LinkedHashSet<>(Set.of("شیعر", "کلاسیک")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Helbest", "Klasîk")))
                .tagsCkb(new LinkedHashSet<>(Set.of("دیوان", "ئەدەب")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Dîwan", "Edebiyat")))
                .seriesId(seriesId)
                .seriesName("دیوانی شیعری کوردی")
                .seriesOrder(1.0)
                .seriesTotalBooks(3)
                .build();

        Writing w2 = Writing.builder()
                .ckbCoverUrl(IMG_OPEN_BOOK)
                .kmrCoverUrl(IMG_OPEN_BOOK)
                .topic(findTopic("WRITING", "شیعر و ئەدەب"))
                .bookGenres(new LinkedHashSet<>(Set.of(BookGenre.POETRY)))
                .publishedByInstitute(true)
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(WritingContent.builder()
                        .title("دیوانی شیعری کوردی - بەرگی دووەم")
                        .description("<p>بەرگی دووەمی دیوانەکە، تایبەت بە شاعیرانی سەدەی ٢٠.</p><img src=\"%s\">".formatted(IMG_OPEN_BOOK))
                        .writer("ئامادەکردنی: تیمی ئەدەبی KHI")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(3_800_000L)
                        .pageCount(310)
                        .genre("شیعری هاوچەرخ")
                        .build())
                .kmrContent(WritingContent.builder()
                        .title("Dîwana helbesta kurdî - Bergê duyem")
                        .description("<p>Bergê duyem ê dîwanê, taybet bi helbestvanên sedsala 20an.</p><img src=\"%s\">".formatted(IMG_OPEN_BOOK))
                        .writer("Amadekirin: Tîma edebî ya KHI")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(3_800_000L)
                        .pageCount(310)
                        .genre("Helbesta hevçerx")
                        .build())
                .keywordsCkb(new LinkedHashSet<>(Set.of("شیعر")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Helbest")))
                .tagsCkb(new LinkedHashSet<>(Set.of("دیوان")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Dîwan")))
                .seriesId(seriesId)
                .seriesName("دیوانی شیعری کوردی")
                .seriesOrder(2.0)
                .seriesTotalBooks(3)
                .build();

        Writing w3 = Writing.builder()
                .ckbCoverUrl(IMG_OLD_DOCUMENT)
                .kmrCoverUrl(IMG_OLD_DOCUMENT)
                .hoverCoverUrl(IMG_ARCHIVE)
                .topic(findTopic("WRITING", "مێژووی کورد"))
                .bookGenres(new LinkedHashSet<>(Set.of(BookGenre.HISTORY)))
                .publishedByInstitute(true)
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(WritingContent.builder()
                        .title("مێژووی نوێی کوردستان")
                        .description("""
                            <h2>توێژینەوەیەکی مێژوویی</h2>
                            <p>کتێبێکی ئەکادیمی لەسەر مێژووی نوێی کوردستان لە سەدەی بیستەم.</p>
                            <img src=\"%s\" alt=\"مێژوو\">
                            """.formatted(IMG_OLD_DOCUMENT))
                        .writer("د. ئاسۆ هەورامی")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(5_200_000L)
                        .pageCount(450)
                        .genre("مێژوو")
                        .build())
                .kmrContent(WritingContent.builder()
                        .title("Dîroka nû ya Kurdistanê")
                        .description("""
                            <h2>Lêkolîneke dîrokî</h2>
                            <p>Pirtûkeke akademîk li ser dîroka nû ya Kurdistanê di sedsala bîstem de.</p>
                            <img src=\"%s\" alt=\"Dîrok\">
                            """.formatted(IMG_OLD_DOCUMENT))
                        .writer("Dr. Aso Hewramî")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(5_200_000L)
                        .pageCount(450)
                        .genre("Dîrok")
                        .build())
                .keywordsCkb(new LinkedHashSet<>(Set.of("مێژوو", "کوردستان")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Dîrok", "Kurdistan")))
                .tagsCkb(new LinkedHashSet<>(Set.of("ئەکادیمی", "مێژوو")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Akademîk", "Dîrok")))
                .build();

        Writing w4 = Writing.builder()
                .ckbCoverUrl(IMG_LIBRARY)
                .kmrCoverUrl(IMG_LIBRARY)
                .topic(findTopic("WRITING", "زمانناسی کوردی"))
                .bookGenres(new LinkedHashSet<>(Set.of(BookGenre.LINGUISTICS, BookGenre.EDUCATIONAL)))
                .publishedByInstitute(true)
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB)))
                .ckbContent(WritingContent.builder()
                        .title("ڕێزمانی زمانی کوردی - سۆرانی")
                        .description("<p>کتێبێکی زانستی لەسەر ڕێزمانی زاراوەی سۆرانی.</p><img src=\"%s\"><a href=\"%s\">داگرتن</a>".formatted(IMG_LIBRARY, PDF_DUMMY))
                        .writer("پ. د. شوان کەریم")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(4_100_000L)
                        .pageCount(380)
                        .genre("زمانناسی")
                        .build())
                .kmrContent(WritingContent.builder().title("").description("").build())
                .keywordsCkb(new LinkedHashSet<>(Set.of("ڕێزمان", "سۆرانی")))
                .tagsCkb(new LinkedHashSet<>(Set.of("زمان", "زانستی")))
                .build();

        Writing w5 = writing(IMG_BOOKS_SHELF, IMG_OPEN_BOOK,
                findTopic("WRITING", "ڕۆمان و چیرۆک"), BookGenre.NOVEL,
                "ڕۆمانی کۆماری مەهاباد",
                "<p>ڕۆمانێکی مێژوویی لەسەر کۆماری مەهاباد ١٩٤٦.</p><img src=\"%s\">".formatted(IMG_BOOKS_SHELF),
                "د. هیوا قادر", 420, "ڕۆمان",
                "Romana Komara Mehabadê",
                "<p>Romaneke dîrokî li ser Komara Mehabadê ya 1946an.</p><img src=\"%s\">".formatted(IMG_BOOKS_SHELF),
                "Dr. Hîwa Qadir", "Roman",
                "ڕۆمان", "Roman", "مەهاباد", "Mehabad", true);

        Writing w6 = writing(IMG_TRADITIONAL, IMG_FOLK_DANCE,
                findTopic("WRITING", "مێژووی کورد"), BookGenre.CULTURAL,
                "فۆلکلۆری کوردی - چیرۆک و داستان",
                "<p>کۆکراوەیەک لە چیرۆک و داستانە فۆلکلۆریە کوردیەکان.</p><img src=\"%s\">".formatted(IMG_TRADITIONAL),
                "ئامادەکردنی: تیمی فۆلکلۆر", 360, "فۆلکلۆر",
                "Folklora kurdî - Çîrok û destan",
                "<p>Berhevokek ji çîrok û destanên folklorîk ên kurdî.</p><img src=\"%s\">".formatted(IMG_TRADITIONAL),
                "Amadekirin: Tîma folklorê", "Folklor",
                "فۆلکلۆر", "Folklor", "چیرۆک", "Çîrok", true);

        Writing w7 = writing(IMG_OPEN_BOOK, IMG_BOOKS_SHELF,
                findTopic("WRITING", "شیعر و ئەدەب"), BookGenre.POETRY,
                "هۆنراوەکانی نالی",
                "<p>کۆی هۆنراوەکانی شاعیری گەورە نالی لەگەڵ لێکدانەوە.</p><img src=\"%s\">".formatted(IMG_OPEN_BOOK),
                "لێکۆڵینەوەی: د. مارف خەزنەدار", 240, "شیعری کلاسیک",
                "Helbestên Nalî",
                "<p>Hemû helbestên helbestvanê mezin Nalî bi şîroveyê re.</p><img src=\"%s\">".formatted(IMG_OPEN_BOOK),
                "Lêkolîn: Dr. Marif Xeznedar", "Helbesta klasîk",
                "نالی", "Nalî", "شیعر", "Helbest", true);

        Writing w8 = writing(IMG_LIBRARY, IMG_OPEN_BOOK,
                findTopic("WRITING", "زمانناسی کوردی"), BookGenre.LINGUISTICS,
                "فەرهەنگی کوردی - کرمانجی",
                "<p>فەرهەنگێکی تەواوی زاراوەی کرمانجی.</p><img src=\"%s\">".formatted(IMG_LIBRARY),
                "د. زانا فەرهادی", 620, "فەرهەنگ",
                "Ferhenga kurdî - Kurmancî",
                "<p>Ferhengeke tam a zaravayê kurmancî.</p><img src=\"%s\">".formatted(IMG_LIBRARY),
                "Dr. Zana Ferhadî", "Ferheng",
                "فەرهەنگ", "Ferheng", "کرمانجی", "Kurmancî", true);

        Writing w9 = writing(IMG_ARCHIVE, IMG_OLD_DOCUMENT,
                findTopic("WRITING", "مێژووی کورد"), BookGenre.HISTORY,
                "ئەرشیفی دۆکیومێنتە مێژووییەکان",
                "<p>کۆکراوەیەک لە دۆکیومێنتە مێژووییە کۆنەکان لەگەڵ شیکردنەوە.</p><img src=\"%s\">".formatted(IMG_ARCHIVE),
                "ئامادەکردنی: ئەرشیفی نەتەوەیی", 540, "مێژوو",
                "Arşîva belgenameyên dîrokî",
                "<p>Berhevokek ji belgenameyên dîrokî yên kevn bi şîroveyê re.</p><img src=\"%s\">".formatted(IMG_ARCHIVE),
                "Amadekirin: Arşîva neteweyî", "Dîrok",
                "ئەرشیف", "Arşîv", "دۆکیومێنت", "Belge", true);

        Writing w10 = writing(IMG_PRESENTER, IMG_LIBRARY,
                findTopic("WRITING", "شیعر و ئەدەب"), BookGenre.EDUCATIONAL,
                "ڕێبەری نووسینی ئەکادیمی",
                "<p>ڕێبەرێکی تەواو بۆ فێربوونی نووسینی ئەکادیمی بە زمانی کوردی.</p><img src=\"%s\"><a href=\"%s\">داگرتن (PDF)</a>".formatted(IMG_PRESENTER, PDF_DUMMY),
                "د. ڕێبین عەلی", 200, "پەروەردە",
                "Rêbera nivîsîna akademîk",
                "<p>Rêbereke tam ji bo fêrbûna nivîsîna akademîk bi zimanê kurdî.</p><img src=\"%s\"><a href=\"%s\">Daxistin (PDF)</a>".formatted(IMG_PRESENTER, PDF_DUMMY),
                "Dr. Rêbîn Elî", "Perwerde",
                "نووسین", "Nivîsîn", "ئەکادیمی", "Akademîk", true);

        writingRepository.saveAll(List.of(w1, w2, w3, w4, w5, w6, w7, w8, w9, w10));
        log.info("[seed:Writing] inserted 10 rows (incl. a 3-book series)");
    }

    /** Builder helper for standalone writings (no series). */
    private Writing writing(String ckbCover, String hoverCover, PublishmentTopic topic, BookGenre genre,
                            String titleCkb, String descCkb, String writerCkb, int pages, String genreLabelCkb,
                            String titleKmr, String descKmr, String writerKmr, String genreLabelKmr,
                            String tagCkb, String tagKmr, String kwCkb, String kwKmr,
                            boolean byInstitute) {
        boolean hasKmr = titleKmr != null && !titleKmr.isEmpty();
        return Writing.builder()
                .ckbCoverUrl(ckbCover)
                .kmrCoverUrl(ckbCover)
                .hoverCoverUrl(hoverCover)
                .topic(topic)
                .bookGenres(new LinkedHashSet<>(Set.of(genre)))
                .publishedByInstitute(byInstitute)
                .contentLanguages(hasKmr
                        ? new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR))
                        : new LinkedHashSet<>(Set.of(Language.CKB)))
                .ckbContent(WritingContent.builder()
                        .title(titleCkb).description(descCkb).writer(writerCkb)
                        .fileUrl(PDF_DUMMY).fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(4_000_000L).pageCount(pages).genre(genreLabelCkb).build())
                .kmrContent(WritingContent.builder()
                        .title(hasKmr ? titleKmr : "").description(hasKmr ? descKmr : "")
                        .writer(hasKmr ? writerKmr : "")
                        .fileUrl(hasKmr ? PDF_DUMMY : null)
                        .fileFormat(hasKmr ? WritingFileFormat.PDF : null)
                        .fileSizeBytes(hasKmr ? 4_000_000L : null)
                        .pageCount(hasKmr ? pages : null)
                        .genre(hasKmr ? genreLabelKmr : "").build())
                .keywordsCkb(new LinkedHashSet<>(Set.of(kwCkb)))
                .keywordsKmr(hasKmr ? new LinkedHashSet<>(Set.of(kwKmr)) : new LinkedHashSet<>())
                .tagsCkb(new LinkedHashSet<>(Set.of(tagCkb)))
                .tagsKmr(hasKmr ? new LinkedHashSet<>(Set.of(tagKmr)) : new LinkedHashSet<>())
                .build();
    }
}