package dio.budgeting.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class Transaction {
    private TransactionId id;
    private UserId userId;
    private AudioRecordId audioRecordId;
    private String description;
    private long amount;
    private Category category;
    private LocalDateTime createdAt;

    public Transaction(UserId userId, AudioRecordId audioRecordId, String description, long amount, Category category) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        this.id = new TransactionId();
        this.userId = userId;
        this.audioRecordId = audioRecordId;
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.createdAt = LocalDateTime.now();
    }

    public Transaction(UserId userId, String description, long amount, Category category) {
        this(userId, null, description, amount, category);
    }
}
