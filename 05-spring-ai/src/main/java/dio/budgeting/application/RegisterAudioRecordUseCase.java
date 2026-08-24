package dio.budgeting.application;

import dio.budgeting.domain.*;
import dio.budgeting.infrastructure.storage.AudioChecksumService;
import org.springframework.stereotype.Service;

@Service
public class RegisterAudioRecordUseCase {

    private final AudioStorageGateway audioStorageGateway;
    private final AudioChecksumService audioChecksumService;
    private final AudioRecordRepository audioRecordRepository;

    public RegisterAudioRecordUseCase(
            AudioStorageGateway audioStorageGateway,
            AudioChecksumService audioChecksumService,
            AudioRecordRepository audioRecordRepository) {
        this.audioStorageGateway = audioStorageGateway;
        this.audioChecksumService = audioChecksumService;
        this.audioRecordRepository = audioRecordRepository;
    }

    public AudioRecord execute(byte[] content, String originalFileName, String contentType, UserId userId) {
        String checksum = audioChecksumService.calculateSha256(content);
        StoredAudio stored = audioStorageGateway.store(content, originalFileName);

        AudioRecord record = new AudioRecord(
                userId,
                originalFileName != null ? originalFileName : "voice_command.mp3",
                stored.storedFileName(),
                contentType != null ? contentType : "audio/mpeg",
                content.length,
                checksum,
                stored.storagePath()
        );

        return audioRecordRepository.save(record);
    }
}
