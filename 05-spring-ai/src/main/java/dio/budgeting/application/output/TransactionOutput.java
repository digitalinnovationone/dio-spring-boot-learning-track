package dio.budgeting.application.output;

import dio.budgeting.domain.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public record TransactionOutput(
        String id,
        String description,
        String category,
        double value,
        String audioRecordId,
        LocalDateTime createdAt) {

    public static TransactionOutput from(Transaction transaction) {
        return new TransactionOutput(
                transaction.getId().uuid().toString(),
                transaction.getDescription(),
                transaction.getCategory().name(),
                BigDecimal.valueOf(transaction.getAmount()).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                transaction.getAudioRecordId() != null ? transaction.getAudioRecordId().uuid().toString() : null,
                transaction.getCreatedAt());
    }
}
