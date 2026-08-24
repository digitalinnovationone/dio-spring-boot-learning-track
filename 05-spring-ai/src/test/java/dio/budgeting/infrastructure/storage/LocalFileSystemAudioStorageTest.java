package dio.budgeting.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileSystemAudioStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldStoreAndLoadAudioFileSuccessfully() {
        var storage = new LocalFileSystemAudioStorage(tempDir.toString());
        byte[] sampleAudio = new byte[]{1, 2, 3, 4, 5};

        var storedAudio = storage.store(sampleAudio, "my_recording.mp3");

        assertThat(storedAudio.storedFileName()).endsWith(".mp3");
        assertThat(storedAudio.storagePath()).isNotEmpty();

        byte[] loaded = storage.load(storedAudio.storagePath());
        assertThat(loaded).isEqualTo(sampleAudio);
    }
}
