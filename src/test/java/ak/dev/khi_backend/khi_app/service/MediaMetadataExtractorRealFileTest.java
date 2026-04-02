package ak.dev.khi_backend.khi_app.service;

import ak.dev.khi_backend.khi_app.service.MediaMetadataExtractor.MediaFileMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke-test against a real JPEG file in uploads/.
 * Only runs when the file exists on disk.
 */
class MediaMetadataExtractorRealFileTest {

    private static final Path REAL_JPEG =
            Path.of("uploads/profile-images/cc9fd518-1962-4c8a-98ec-cb0b5dd7007e.jpg");

    static boolean fileExists() {
        return Files.exists(REAL_JPEG);
    }

    @Test
    @EnabledIf("fileExists")
    void extractsRealJpegMetadata() throws Exception {
        MediaMetadataExtractor extractor = new MediaMetadataExtractor();
        byte[] bytes = Files.readAllBytes(REAL_JPEG);

        MediaFileMeta meta = extractor.extract(bytes, "image/jpeg", "akar.jpg");

        System.out.println("=== Real JPEG extraction result ===");
        System.out.println("  format     = " + meta.getFileFormat());
        System.out.println("  width      = " + meta.getWidthPx());
        System.out.println("  height     = " + meta.getHeightPx());
        System.out.println("  duration   = " + meta.getDurationSeconds());
        System.out.println("  codec      = " + meta.getCodec());
        System.out.println("  bitrate    = " + meta.getBitrateKbps());
        System.out.println("  hasData    = " + meta.hasData());

        assertThat(meta.hasData()).isTrue();
        assertThat(meta.getFileFormat()).isEqualTo("JPEG");
        assertThat(meta.getWidthPx()).isNotNull().isGreaterThan(0);
        assertThat(meta.getHeightPx()).isNotNull().isGreaterThan(0);
    }
}

