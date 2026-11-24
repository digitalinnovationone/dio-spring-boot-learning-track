package dio.budgeting.application.output;

import dio.budgeting.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionOutput(String id, String description, String category, BigDecimal value, LocalDate date) {
    public static TransactionOutput from(Transaction transaction) {
        return new TransactionOutput(
                transaction.getId().uuid().toString(),
                transaction.getDescription(),
                transaction.getCategory().name(),
                Money.fromCents(transaction.getAmount()),
                transaction.getDate());
    }
}
