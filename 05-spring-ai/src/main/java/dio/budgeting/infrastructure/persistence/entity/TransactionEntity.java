package dio.budgeting.infrastructure.persistence.entity;

import dio.budgeting.domain.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = true)
    private UUID audioRecordId;

    private String description;
    private long amount;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static TransactionEntity from(Transaction transaction) {
        return new TransactionEntity(
                transaction.getId().uuid(),
                transaction.getUserId().uuid(),
                transaction.getAudioRecordId() != null ? transaction.getAudioRecordId().uuid() : null,
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getCreatedAt() != null ? transaction.getCreatedAt() : LocalDateTime.now());
    }

    public Transaction toDomain() {
        return new Transaction(
                new TransactionId(this.id),
                new UserId(this.userId),
                this.audioRecordId != null ? new AudioRecordId(this.audioRecordId) : null,
                this.description,
                this.amount,
                this.category,
                this.createdAt
        );
    }
}
