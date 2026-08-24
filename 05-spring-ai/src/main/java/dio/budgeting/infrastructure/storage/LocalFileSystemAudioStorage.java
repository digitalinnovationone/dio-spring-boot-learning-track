package dio.budgeting.infrastructure.storage;

import dio.budgeting.domain.AudioStorageGateway;
import dio.budgeting.domain.StoredAudio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class LocalFileSystemAudioStorage implements AudioStorageGateway {

    private final Path storageDirectory;

    public LocalFileSystemAudioStorage(@Value("${storage.audio.directory:./uploads/audio}") String storageDirectory) {
        this.storageDirectory = Paths.get(storageDirectory);
    }

    @Override
    public StoredAudio store(byte[] content, String originalFileName) {
        try {
            if (!Files.exists(storageDirectory)) {
                Files.createDirectories(storageDirectory);
            }

            String extension = extractExtension(originalFileName);
            String storedFileName = UUID.randomUUID() + extension;
            Path destination = storageDirectory.resolve(storedFileName);

            Files.write(destination, content);

            return new StoredAudio(storedFileName, destination.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store audio file locally", e);
        }
    }

    @Override
    public byte[] load(String storagePath) {
        try {
            return Files.readAllBytes(Paths.get(storagePath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read audio file from " + storagePath, e);
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ".mp3";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }
}
