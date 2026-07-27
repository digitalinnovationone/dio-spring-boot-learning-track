package dio.budgeting.infrastructure.http.audio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

/**
 * Rejects unusable audio uploads locally, before any call to
 * TranscriptionModel/ChatClient/TextToSpeechModel. Content-type matching is a
 * declared-header check, not content inspection: a renamed file (e.g.
 * {@code arquivo.exe} sent as {@code audio/mpeg}) is not detected here.
 */
@Component
public class AudioFileValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "audio/mpeg", "audio/mp3",
            "audio/mp4",
            "audio/m4a", "audio/x-m4a",
            "audio/wav", "audio/x-wav",
            "audio/webm"
    );

    private final DataSize maxSize;

    public AudioFileValidator(@Value("${app.audio.max-size}") String maxSize) {
        this.maxSize = DataSize.parse(maxSize);
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAudioFileException(InvalidAudioFileException.Reason.EMPTY,
                    "O arquivo de áudio não pode estar vazio.");
        }
        if (file.getSize() > maxSize.toBytes()) {
            throw new InvalidAudioFileException(InvalidAudioFileException.Reason.TOO_LARGE,
                    "O arquivo de áudio excede o tamanho máximo permitido.");
        }
        var contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidAudioFileException(InvalidAudioFileException.Reason.MISSING_CONTENT_TYPE,
                    "Não foi possível identificar o tipo do arquivo de áudio.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidAudioFileException(InvalidAudioFileException.Reason.UNSUPPORTED_TYPE,
                    "O tipo do arquivo de áudio não é permitido.");
        }
    }
}
