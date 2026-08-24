package dio.budgeting.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AudioRecord {
    private AudioRecordId id;
    private UserId userId;
    private String originalFileName;
    private String storedFileName;
    private String contentType;
    private long fileSize;
    private String checksumSha256;
    private String storagePath;
    private LocalDateTime createdAt;

    public AudioRecord(
            UserId userId,
            String originalFileName,
            String storedFileName,
            String contentType,
            long fileSize,
            String checksumSha256,
            String storagePath) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        this.id = new AudioRecordId();
        this.userId = userId;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.checksumSha256 = checksumSha256;
        this.storagePath = storagePath;
        this.createdAt = LocalDateTime.now();
    }
}
