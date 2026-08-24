package dio.budgeting.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioRecordTest {

    @Test
    void shouldCreateAudioRecordWithCorrectAttributes() {
        var userId = new UserId();
        var record = new AudioRecord(
                userId,
                "voice.mp3",
                "internal_uuid.mp3",
                "audio/mpeg",
                1024L,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "/uploads/audio/internal_uuid.mp3"
        );

        assertThat(record.getId()).isNotNull();
        assertThat(record.getUserId()).isEqualTo(userId);
        assertThat(record.getOriginalFileName()).isEqualTo("voice.mp3");
        assertThat(record.getStoredFileName()).isEqualTo("internal_uuid.mp3");
        assertThat(record.getContentType()).isEqualTo("audio/mpeg");
        assertThat(record.getFileSize()).isEqualTo(1024L);
        assertThat(record.getChecksumSha256()).hasSize(64);
        assertThat(record.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldThrowExceptionWhenUserIdIsNull() {
        assertThatThrownBy(() -> new AudioRecord(null, "f.mp3", "s.mp3", "a/m", 10L, "hash", "/p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UserId cannot be null");
    }
}
