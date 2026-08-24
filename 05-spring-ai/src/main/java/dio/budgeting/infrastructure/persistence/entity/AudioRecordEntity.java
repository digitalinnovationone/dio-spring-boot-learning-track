package dio.budgeting.infrastructure.persistence.entity;

import dio.budgeting.domain.AudioRecord;
import dio.budgeting.domain.AudioRecordId;
import dio.budgeting.domain.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audio_records")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AudioRecordEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String storedFileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false, length = 64)
    private String checksumSha256;

    @Column(nullable = false)
    private String storagePath;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static AudioRecordEntity from(AudioRecord record) {
        return new AudioRecordEntity(
                record.getId().uuid(),
                record.getUserId().uuid(),
                record.getOriginalFileName(),
                record.getStoredFileName(),
                record.getContentType(),
                record.getFileSize(),
                record.getChecksumSha256(),
                record.getStoragePath(),
                record.getCreatedAt()
        );
    }

    public AudioRecord toDomain() {
        return new AudioRecord(
                new AudioRecordId(this.id),
                new UserId(this.userId),
                this.originalFileName,
                this.storedFileName,
                this.contentType,
                this.fileSize,
                this.checksumSha256,
                this.storagePath,
                this.createdAt
        );
    }
}
