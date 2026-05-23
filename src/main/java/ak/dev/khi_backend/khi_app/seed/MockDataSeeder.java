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
 * seed step is skipped — so it is safe to leave enabled across restarts
 * during development.</p>
 *
 * <p><b>Resilience.</b>  Each section is wrapped in its own try/catch so
 * that a problem in one module (e.g. unique-constraint clash on a slug)
 * does not block the rest.</p>
 *
 * <p><b>Media sources.</b>  All media URLs point at well-known stable
 * public CDNs:
 * <ul>
 *   <li>Images — <code>images.unsplash.com</code> direct asset URLs.</li>
 *   <li>Videos — <code>commondatastorage.googleapis.com</code> sample MP4s.</li>
 *   <li>Audio  — <code>soundhelix.com</code> royalty-free MP3 examples.</li>
 *   <li>PDFs   — <code>w3.org</code> dummy PDFs.</li>
 * </ul>
 * </p>
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

        aboutRepository.saveAll(List.of(a1, a2, a3));
        log.info("[seed:About] inserted 3 rows");
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

        contactRepository.saveAll(List.of(c1, c2, c3));
        log.info("[seed:Contact] inserted 3 rows");
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

        serviceRepository.saveAll(List.of(s1, s2, s3, s4));
        log.info("[seed:Service] inserted 4 rows");
    }

    // =========================================================================
    // 4) NEWS
    // =========================================================================

    @Transactional
    public void seedNews() {
        if (newsRepository.count() > 0) {
            log.info("[seed:News] table not empty — skipping");
            return;
        }

        NewsCategory catCulture = newsCategoryRepository.save(NewsCategory.builder()
                .nameCkb("کلتوور")
                .nameKmr("Çand")
                .build());
        NewsCategory catHistory = newsCategoryRepository.save(NewsCategory.builder()
                .nameCkb("مێژوو")
                .nameKmr("Dîrok")
                .build());
        NewsCategory catLiterature = newsCategoryRepository.save(NewsCategory.builder()
                .nameCkb("ئەدەب")
                .nameKmr("Edebiyat")
                .build());

        NewsSubCategory subFolk = newsSubCategoryRepository.save(NewsSubCategory.builder()
                .nameCkb("فۆلکلۆر")
                .nameKmr("Folklor")
                .category(catCulture)
                .build());
        NewsSubCategory subAncient = newsSubCategoryRepository.save(NewsSubCategory.builder()
                .nameCkb("مێژووی کۆن")
                .nameKmr("Dîroka kevn")
                .category(catHistory)
                .build());
        NewsSubCategory subPoetry = newsSubCategoryRepository.save(NewsSubCategory.builder()
                .nameCkb("شیعر")
                .nameKmr("Helbest")
                .category(catLiterature)
                .build());
        NewsSubCategory subNovel = newsSubCategoryRepository.save(NewsSubCategory.builder()
                .nameCkb("ڕۆمان")
                .nameKmr("Roman")
                .category(catLiterature)
                .build());

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
                .category(catHistory)
                .subCategory(subAncient)
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
                .category(catCulture)
                .subCategory(subFolk)
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
                .category(catLiterature)
                .subCategory(subPoetry)
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
                .category(catHistory)
                .subCategory(subAncient)
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
                .category(catLiterature)
                .subCategory(subNovel)
                .build();

        newsRepository.saveAll(List.of(n1, n2, n3, n4, n5));
        log.info("[seed:News] inserted 5 rows (3 categories, 4 sub-categories)");
    }

    // =========================================================================
    // 5) PROJECT
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

        ProjectKeyword kwKurdish   = ensureKeyword("Kurdish");
        ProjectKeyword kwHeritage  = ensureKeyword("Heritage");
        ProjectKeyword kwResearch  = ensureKeyword("Research");

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

        projectRepository.saveAll(List.of(p1, p2, p3, p4));
        log.info("[seed:Project] inserted 4 rows");
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
                topic("VIDEO",   "بەڵگەنامەی مێژوویی",    "Belgefîlma dîrokî"),
                topic("VIDEO",   "فیلمی ئەدەبی",          "Fîlma edebî"),
                topic("VIDEO",   "کلیپی مۆسیقا",          "Klîpên muzîkê"),
                topic("IMAGE",   "گەلەری مێژوویی",        "Galeriya dîrokî"),
                topic("IMAGE",   "وێنەی فۆلکلۆری",       "Wêneyên folklorîk"),
                topic("IMAGE",   "ژیانی ڕۆژانە",          "Jiyana rojane"),
                topic("WRITING", "شیعر و ئەدەب",          "Helbest û edebiyat"),
                topic("WRITING", "مێژووی کورد",           "Dîroka kurdî"),
                topic("WRITING", "زمانناسی کوردی",        "Zimanzaniya kurdî")
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
    // 7) SOUND TRACK
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

        soundTrackRepository.saveAll(List.of(st1, st2, st3));
        log.info("[seed:SoundTrack] inserted 3 rows (with files, brochures, attachments)");
    }

    // =========================================================================
    // 8) VIDEO
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

        videoRepository.saveAll(List.of(v1, v2, v3));
        log.info("[seed:Video] inserted 3 rows (1 FILM, 1 VIDEO_CLIP with 3 clips, 1 FILM)");
    }

    // =========================================================================
    // 9) IMAGE COLLECTION
    // =========================================================================

    @Transactional
    public void seedImageCollections() {
        if (imageCollectionRepository.count() > 0) {
            log.info("[seed:ImageCollection] table not empty — skipping");
            return;
        }

        ImageCollection ic1 = ImageCollection.builder()
                .collectionType(ImageCollectionType.GALLERY)
                .ckbCoverUrl(IMG_ERBIL_CITADEL)
                .kmrCoverUrl(IMG_ERBIL_CITADEL)
                .hoverCoverUrl(IMG_MOUNTAINS_KRD)
                .topic(findTopic("IMAGE", "گەلەری مێژوویی"))
                .ckbContent(ImageContent.builder()
                        .title("گەلەری وێنەی قەڵای هەولێر")
                        .description("""
                            <h2>وێنە کۆنەکان</h2>
                            <p>کۆکراوەیەکی تایبەت لە وێنەی کۆنی قەڵای هەولێر، یەکێک لە کۆنترین شارە دانیشتراوەکانی جیهان.</p>
                            <img src=\"%s\" alt=\"قەڵا\">
                            <p>وێنەکان لە ساڵانی ١٩٠٠ - ١٩٥٠ گیراون.</p>
                            """.formatted(IMG_ERBIL_CITADEL))
                        .location("هەولێر، هەرێمی کوردستان")
                        .collectedBy("ئەرشیفی KHI")
                        .build())
                .kmrContent(ImageContent.builder()
                        .title("Galeriya wêneyên Kelaya Hewlêrê")
                        .description("""
                            <h2>Wêneyên kevn</h2>
                            <p>Berhevokeke taybet ji wêneyên kevn ên Kelaya Hewlêrê, yek ji kevintirîn bajarên niştecîh ên cîhanê.</p>
                            <img src=\"%s\" alt=\"Kela\">
                            <p>Wêne di salên 1900-1950an de hatine girtin.</p>
                            """.formatted(IMG_ERBIL_CITADEL))
                        .location("Hewlêr, Herêma Kurdistanê")
                        .collectedBy("Arşîva KHI")
                        .build())
                .publishmentDate(LocalDate.now().minusMonths(3))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of("قەڵا", "مێژوو", "هەولێر")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Kela", "Dîrok", "Hewlêr")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("کۆن", "ئەرشیف")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Kevn", "Arşîv")))
                .build();

        addAlbumItem(ic1, IMG_ERBIL_CITADEL, "قەڵای هەولێر لە دوورەوە", "Kelaya Hewlêrê ji dûr ve", 0);
        addAlbumItem(ic1, IMG_OLD_DOCUMENT, "دۆکیومێنتی کۆن لە ناو قەڵا", "Belgenameyên kevn di nava kelayê de", 1);
        addAlbumItem(ic1, IMG_BUILDING, "بینایەکی کۆن", "Avahiyek kevn", 2);
        addAlbumItem(ic1, IMG_TRADITIONAL, "ژینگەی تەقلیدی", "Hawîrdora kevneşopî", 3);

        ImageCollection ic2 = ImageCollection.builder()
                .collectionType(ImageCollectionType.SINGLE)
                .ckbCoverUrl(IMG_CALLIGRAPHY)
                .kmrCoverUrl(IMG_CALLIGRAPHY)
                .topic(findTopic("IMAGE", "وێنەی فۆلکلۆری"))
                .ckbContent(ImageContent.builder()
                        .title("نموونەی خوسنۆڤی کوردی")
                        .description("<p>نموونەیەک لە خوسنۆڤی کلاسیکی کوردی.</p><img src=\"%s\">".formatted(IMG_CALLIGRAPHY))
                        .location("سلێمانی")
                        .collectedBy("ئاکار شالی")
                        .build())
                .kmrContent(ImageContent.builder()
                        .title("Nimûneyek xetatiya kurdî")
                        .description("<p>Nimûneyek ji xetatiya klasîk a kurdî.</p><img src=\"%s\">".formatted(IMG_CALLIGRAPHY))
                        .location("Silêmanî")
                        .collectedBy("Akar Şalî")
                        .build())
                .publishmentDate(LocalDate.now().minusMonths(1))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of("خوسنۆڤی")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Xetatî")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("هونەر")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Huner")))
                .build();
        addAlbumItem(ic2, IMG_CALLIGRAPHY, "خوسنۆڤی", "Xetatî", 0);

        ImageCollection ic3 = ImageCollection.builder()
                .collectionType(ImageCollectionType.PHOTO_STORY)
                .ckbCoverUrl(IMG_TRADITIONAL)
                .kmrCoverUrl(IMG_TRADITIONAL)
                .topic(findTopic("IMAGE", "ژیانی ڕۆژانە"))
                .ckbContent(ImageContent.builder()
                        .title("چیرۆکی وێنە: درووستکردنی فرشی کوردی")
                        .description("""
                            <h2>چیرۆکی پیشە</h2>
                            <p>وێنە بە وێنە چۆن فرشی کوردی درووست دەکرێت، لە دەستپێکی کارەکەوە تا کۆتایی.</p>
                            <img src=\"%s\">
                            """.formatted(IMG_TRADITIONAL))
                        .location("شاری هەڵەبجە")
                        .collectedBy("KHI Field Team")
                        .build())
                .kmrContent(ImageContent.builder()
                        .title("Çîroka wêneyan: Çêkirina farşê kurdî")
                        .description("""
                            <h2>Çîroka pîşeyê</h2>
                            <p>Wêne bi wêne çawa farşê kurdî tê çêkirin, ji destpêka kar heta dawiyê.</p>
                            <img src=\"%s\">
                            """.formatted(IMG_TRADITIONAL))
                        .location("Bajarê Helebceyê")
                        .collectedBy("Tîma KHI ya Berbihê")
                        .build())
                .publishmentDate(LocalDate.now().minusWeeks(2))
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .tagsCkb(new LinkedHashSet<>(Set.of("فرش", "پیشە")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Farş", "Pîşe")))
                .keywordsCkb(new LinkedHashSet<>(Set.of("فۆلکلۆر", "دەستکردن")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Folklor", "Destçêk")))
                .build();

        for (int i = 0; i < 5; i++) {
            String img = switch (i) {
                case 0 -> IMG_TRADITIONAL;
                case 1 -> IMG_FOLK_DANCE;
                case 2 -> IMG_BUILDING;
                case 3 -> IMG_ARCHIVE;
                default -> IMG_OPEN_BOOK;
            };
            addAlbumItem(ic3, img, "هەنگاو " + (i + 1), "Gav " + (i + 1), i);
        }

        imageCollectionRepository.saveAll(List.of(ic1, ic2, ic3));
        log.info("[seed:ImageCollection] inserted 3 rows (1 GALLERY+4, 1 SINGLE+1, 1 PHOTO_STORY+5)");
    }

    private void addAlbumItem(ImageCollection col, String imageUrl, String captionCkb, String captionKmr, int sortOrder) {
        ImageAlbumItem item = ImageAlbumItem.builder()
                .imageUrl(imageUrl)
                .captionCkb(captionCkb)
                .captionKmr(captionKmr)
                .descriptionCkb("<p>" + captionCkb + " — وردەکاری زیاتر.</p>")
                .descriptionKmr("<p>" + captionKmr + " — hûrgilî zêdetir.</p>")
                .sortOrder(sortOrder)
                .fileSizeBytes(450_000L)
                .widthPx(1600)
                .heightPx(1067)
                .mimeType("image/jpeg")
                .imageCollection(col)
                .build();
        col.getImageAlbum().add(item);
    }

    // =========================================================================
    // 10) WRITING
    // =========================================================================

    @Transactional
    public void seedWritings() {
        if (writingRepository.count() > 0) {
            log.info("[seed:Writing] table not empty — skipping");
            return;
        }

        Writing w1 = Writing.builder()
                .ckbCoverUrl(IMG_BOOKS_SHELF)
                .kmrCoverUrl(IMG_BOOKS_SHELF)
                .hoverCoverUrl(IMG_OPEN_BOOK)
                .topic(findTopic("WRITING", "شیعر و ئەدەب"))
                .bookGenres(new LinkedHashSet<>(Set.of(BookGenre.POETRY, BookGenre.CULTURAL)))
                .publishedByInstitute(true)
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(WritingContent.builder()
                        .title("دیوانی شیعری شێرکۆ بێکەس")
                        .description("""
                            <h2>دیوانی شیعر</h2>
                            <p>دیوانی تەواوی شیعرەکانی شاعیر شێرکۆ بێکەس ، یەکێک لە گەورەترین شاعیرانی کورد لە سەدەی ٢١.</p>
                            <img src=\"%s\" alt=\"دیوان\">
                            <p>کتێبەکە زیاتر لە ٢٠٠ شیعری هەڵبژێراو لەخۆ دەگرێت لە ساڵانی ١٩٧٠ - ٢٠١٣.</p>
                            <a href=\"%s\">نموونەیەک لە کتێبەکە</a>
                            """.formatted(IMG_BOOKS_SHELF, PDF_DUMMY))
                        .writer("شێرکۆ بێکەس")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(15_000_000L)
                        .pageCount(450)
                        .genre("شیعر")
                        .build())
                .kmrContent(WritingContent.builder()
                        .title("Dîwana helbestên Şêrko Bêkes")
                        .description("""
                            <h2>Dîwana helbestê</h2>
                            <p>Dîwana tam a helbestên helbestvan Şêrko Bêkes, yek ji helbestvanên herî mezin ên kurd ên sedsala 21ê.</p>
                            <img src=\"%s\" alt=\"Dîwan\">
                            <p>Pirtûk zêdetirî 200 helbestên hilbijartî dihewîne ji salên 1970 heta 2013.</p>
                            <a href=\"%s\">Nimûneyek ji pirtûkê</a>
                            """.formatted(IMG_BOOKS_SHELF, PDF_DUMMY))
                        .writer("Şêrko Bêkes")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(15_500_000L)
                        .pageCount(465)
                        .genre("Helbest")
                        .build())
                .keywordsCkb(new LinkedHashSet<>(Set.of("شێرکۆ", "شیعر", "کلاسیک")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Şêrko", "Helbest", "Klasîk")))
                .tagsCkb(new LinkedHashSet<>(Set.of("ئەدەب", "نوێ")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Edebiyat", "Nû")))
                .seriesId("series-sherko-001")
                .seriesName("کۆکراوەی شێرکۆ")
                .seriesOrder(1.0)
                .seriesTotalBooks(3)
                .build();

        Writing w2 = Writing.builder()
                .ckbCoverUrl(IMG_NEWSPAPER)
                .kmrCoverUrl(IMG_NEWSPAPER)
                .topic(findTopic("WRITING", "مێژووی کورد"))
                .bookGenres(new LinkedHashSet<>(Set.of(BookGenre.HISTORY, BookGenre.EDUCATIONAL)))
                .publishedByInstitute(true)
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(WritingContent.builder()
                        .title("مێژووی نوێی کوردستان ١٩٠٠ - ٢٠٠٠")
                        .description("""
                            <p>کتێبێکی ئەکادیمی فراوان لەسەر مێژووی نوێی کوردستان ، نووسراوی پروفیسۆر ئەحمەد فاروقی.</p>
                            <img src=\"%s\">
                            <a href=\"%s\">کتێبی تەواو</a>
                            """.formatted(IMG_NEWSPAPER, PDF_DUMMY))
                        .writer("پروفیسۆر ئەحمەد فاروقی")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(28_000_000L)
                        .pageCount(820)
                        .genre("مێژوو")
                        .build())
                .kmrContent(WritingContent.builder()
                        .title("Dîroka nû ya Kurdistanê 1900-2000")
                        .description("""
                            <p>Pirtûkeke akademîk a berfireh li ser dîroka nû ya Kurdistanê, ji aliyê profesor Ehmed Farûqî ve hatiye nivîsîn.</p>
                            <img src=\"%s\">
                            <a href=\"%s\">Pirtûka tam</a>
                            """.formatted(IMG_NEWSPAPER, PDF_DUMMY))
                        .writer("Profesor Ehmed Farûqî")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(29_000_000L)
                        .pageCount(845)
                        .genre("Dîrok")
                        .build())
                .keywordsCkb(new LinkedHashSet<>(Set.of("مێژوو", "سەدەی٢٠")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Dîrok", "Sedsala20")))
                .tagsCkb(new LinkedHashSet<>(Set.of("ئەکادیمی", "توێژینەوە")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Akademîk", "Lêkolîn")))
                .seriesId("series-history-001")
                .seriesName("کۆکراوەی مێژوو")
                .seriesOrder(1.0)
                .seriesTotalBooks(5)
                .build();

        Writing w3 = Writing.builder()
                .ckbCoverUrl(IMG_OPEN_BOOK)
                .kmrCoverUrl(IMG_OPEN_BOOK)
                .topic(findTopic("WRITING", "زمانناسی کوردی"))
                .bookGenres(new LinkedHashSet<>(Set.of(BookGenre.LINGUISTICS, BookGenre.EDUCATIONAL)))
                .publishedByInstitute(true)
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB)))
                .ckbContent(WritingContent.builder()
                        .title("ڕێزمانی هاوچەرخی زمانی سۆرانی")
                        .description("""
                            <p>کتێبێکی پێداگۆژی ڕێزمانی زمانی سۆرانی بۆ قوتابخانە ئامادەییەکان.</p>
                            <img src=\"%s\">
                            """.formatted(IMG_OPEN_BOOK))
                        .writer("دکتۆر هیمن مەلا فەرید")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.PDF)
                        .fileSizeBytes(8_000_000L)
                        .pageCount(280)
                        .genre("زمانناسی")
                        .build())
                .kmrContent(WritingContent.builder().title("").description("").build())
                .keywordsCkb(new LinkedHashSet<>(Set.of("ڕێزمان", "سۆرانی")))
                .tagsCkb(new LinkedHashSet<>(Set.of("زمان", "خوێندنگە")))
                .build();

        Writing w4 = Writing.builder()
                .ckbCoverUrl(IMG_OPEN_BOOK)
                .kmrCoverUrl(IMG_OPEN_BOOK)
                .topic(findTopic("WRITING", "شیعر و ئەدەب"))
                .bookGenres(new LinkedHashSet<>(Set.of(BookGenre.NOVEL)))
                .publishedByInstitute(false)
                .contentLanguages(new LinkedHashSet<>(Set.of(Language.CKB, Language.KMR)))
                .ckbContent(WritingContent.builder()
                        .title("لۆتکەی شیعرگۆیی - ڕۆمانێکی مێژوویی")
                        .description("""
                            <p>ڕۆمانێکی مێژوویی لەبارەی ژیانی شاعیرێکی کورد لە سەردەمی عوسمانیەکان.</p>
                            <img src=\"%s\">
                            """.formatted(IMG_OPEN_BOOK))
                        .writer("بەختیار عەلی")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.EPUB)
                        .fileSizeBytes(3_500_000L)
                        .pageCount(420)
                        .genre("ڕۆمان")
                        .build())
                .kmrContent(WritingContent.builder()
                        .title("Lûtkeya helbestê - Romaneke dîrokî")
                        .description("""
                            <p>Romaneke dîrokî li ser jiyana helbestvanekî kurd di serdema osmaniyan de.</p>
                            <img src=\"%s\">
                            """.formatted(IMG_OPEN_BOOK))
                        .writer("Bextiyar Elî")
                        .fileUrl(PDF_DUMMY)
                        .fileFormat(WritingFileFormat.EPUB)
                        .fileSizeBytes(3_800_000L)
                        .pageCount(440)
                        .genre("Roman")
                        .build())
                .keywordsCkb(new LinkedHashSet<>(Set.of("ڕۆمان", "بەختیار")))
                .keywordsKmr(new LinkedHashSet<>(Set.of("Roman", "Bextiyar")))
                .tagsCkb(new LinkedHashSet<>(Set.of("ئەدەب", "ڕۆمان")))
                .tagsKmr(new LinkedHashSet<>(Set.of("Edebiyat", "Roman")))
                .build();

        writingRepository.saveAll(List.of(w1, w2, w3, w4));
        log.info("[seed:Writing] inserted 4 rows");
    }
}
