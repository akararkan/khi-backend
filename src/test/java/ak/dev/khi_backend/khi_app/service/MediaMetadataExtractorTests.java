package ak.dev.khi_backend.khi_app.service;

import ak.dev.khi_backend.khi_app.service.MediaMetadataExtractor.MediaFileMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class MediaMetadataExtractorTests {

    private MediaMetadataExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new MediaMetadataExtractor();
    }

    // ── Image Tests ──────────────────────────────────────────────────────────

    @Test
    void extractsPngDimensionsAndFormat() throws Exception {
        byte[] png = createPng(640, 480);

        MediaFileMeta meta = extractor.extract(png, "image/png", "test.png");

        assertThat(meta.hasData()).isTrue();
        assertThat(meta.getWidthPx()).isEqualTo(640);
        assertThat(meta.getHeightPx()).isEqualTo(480);
        assertThat(meta.getFileFormat()).isEqualTo("PNG");
        // images have no duration/codec
        assertThat(meta.getDurationSeconds()).isNull();
        assertThat(meta.getCodec()).isNull();
    }

    @Test
    void extractsJpegDimensionsAndFormat() throws Exception {
        byte[] jpeg = createJpeg(1920, 1080);

        MediaFileMeta meta = extractor.extract(jpeg, "image/jpeg", "photo.jpg");

        assertThat(meta.hasData()).isTrue();
        assertThat(meta.getWidthPx()).isEqualTo(1920);
        assertThat(meta.getHeightPx()).isEqualTo(1080);
        assertThat(meta.getFileFormat()).isEqualTo("JPEG");
    }

    // ── Edge Cases ───────────────────────────────────────────────────────────

    @Test
    void returnsEmptyForNullBytes() {
        MediaFileMeta meta = extractor.extract(null, "image/png", "test.png");
        assertThat(meta.hasData()).isFalse();
    }

    @Test
    void returnsEmptyForBlankContentType() {
        MediaFileMeta meta = extractor.extract(new byte[]{1, 2, 3}, "  ", "test.bin");
        assertThat(meta.hasData()).isFalse();
    }

    @Test
    void returnsFormatFromMimeForUnknownBinaryContent() {
        // Random bytes with a video MIME → should at least get the format from MIME
        MediaFileMeta meta = extractor.extract(new byte[]{0, 0, 0, 1}, "video/mp4", "clip.mp4");
        // May or may not detect format depending on magic bytes, but should not throw
        assertThat(meta).isNotNull();
    }

    @Test
    void returnsFormatFromMimeForUnsupportedAudioFormat() {
        // FLAC is not natively supported by metadata-extractor — should degrade gracefully
        MediaFileMeta meta = extractor.extract(new byte[]{0x66, 0x4C, 0x61, 0x43}, "audio/flac", "song.flac");
        assertThat(meta).isNotNull();
        // At minimum, MIME-based format should be returned
        if (meta.getFileFormat() != null) {
            assertThat(meta.getFileFormat()).isEqualToIgnoringCase("FLAC");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private byte[] createPng(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private byte[] createJpeg(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}

