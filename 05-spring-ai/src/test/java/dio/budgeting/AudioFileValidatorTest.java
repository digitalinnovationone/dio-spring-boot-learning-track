package dio.budgeting;

import dio.budgeting.infrastructure.http.audio.AudioFileValidator;
import dio.budgeting.infrastructure.http.audio.InvalidAudioFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link AudioFileValidator}: no Spring context. Size
 * boundaries are exercised via a mocked {@link MultipartFile#getSize()}
 * instead of allocating large byte arrays.
 */
class AudioFileValidatorTest {

    private static final long TEN_MB = 10L * 1024 * 1024;

    private final AudioFileValidator validator = new AudioFileValidator("10MB");

    // --- Valid ---

    @Test
    void acceptsMp3File() {
        var file = new MockMultipartFile("file", "audio.mp3", "audio/mpeg", new byte[]{1, 2, 3});

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsWavFile() {
        var file = new MockMultipartFile("file", "audio.wav", "audio/wav", new byte[]{1, 2, 3});

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsM4aFile() {
        var file = new MockMultipartFile("file", "audio.m4a", "audio/x-m4a", new byte[]{1, 2, 3});

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsFile_exactlyAtSizeLimit() {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(TEN_MB);
        when(file.getContentType()).thenReturn("audio/mpeg");

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    // --- Invalid ---

    @Test
    void rejectsNullFile() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidAudioFileException.class)
                .hasMessage("O arquivo de áudio não pode estar vazio.")
                .extracting(ex -> ((InvalidAudioFileException) ex).getReason())
                .isEqualTo(InvalidAudioFileException.Reason.EMPTY);
    }

    @Test
    void rejectsEmptyFile() {
        var file = new MockMultipartFile("file", "audio.mp3", "audio/mpeg", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidAudioFileException.class)
                .hasMessage("O arquivo de áudio não pode estar vazio.");
    }

    @Test
    void rejectsNullContentType() {
        var file = new MockMultipartFile("file", "audio.mp3", null, new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidAudioFileException.class)
                .hasMessage("Não foi possível identificar o tipo do arquivo de áudio.")
                .extracting(ex -> ((InvalidAudioFileException) ex).getReason())
                .isEqualTo(InvalidAudioFileException.Reason.MISSING_CONTENT_TYPE);
    }

    @Test
    void rejectsBlankContentType() {
        var file = new MockMultipartFile("file", "audio.mp3", "   ", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidAudioFileException.class)
                .hasMessage("Não foi possível identificar o tipo do arquivo de áudio.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/octet-stream", "text/plain", "image/png", "video/mp4"})
    void rejectsDisallowedContentType(String contentType) {
        var file = new MockMultipartFile("file", "audio.mp3", contentType, new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidAudioFileException.class)
                .hasMessage("O tipo do arquivo de áudio não é permitido.")
                .extracting(ex -> ((InvalidAudioFileException) ex).getReason())
                .isEqualTo(InvalidAudioFileException.Reason.UNSUPPORTED_TYPE);
    }

    @Test
    void rejectsFile_aboveSizeLimit() {
        var file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(TEN_MB + 1);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidAudioFileException.class)
                .hasMessage("O arquivo de áudio excede o tamanho máximo permitido.")
                .extracting(ex -> ((InvalidAudioFileException) ex).getReason())
                .isEqualTo(InvalidAudioFileException.Reason.TOO_LARGE);
    }
}
