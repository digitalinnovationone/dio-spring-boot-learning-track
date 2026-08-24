package dio.budgeting.infrastructure.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioChecksumServiceTest {

    private final AudioChecksumService checksumService = new AudioChecksumService();

    @Test
    void shouldCalculateCorrectSha256Checksum() {
        byte[] content = "audio-recording-test-sample".getBytes(StandardCharsets.UTF_8);

        String hash = checksumService.calculateSha256(content);

        assertThat(hash).isNotBlank();
        assertThat(hash).hasSize(64); // SHA-256 hex string has 64 characters
    }

    @Test
    void shouldThrowExceptionWhenContentIsNull() {
        assertThatThrownBy(() -> checksumService.calculateSha256(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
