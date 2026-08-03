package dio.budgeting.application.output;

import dio.budgeting.domain.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record TransactionOutput(String id, String description, String category, double value) {
    public static TransactionOutput from(Transaction transaction) {
        return new TransactionOutput(
                transaction.getId().uuid().toString(),
                transaction.getDescription(),
                transaction.getCategory().name(),
                toReais(transaction.getAmount()));
    }

    /**
     * Converte um valor em centavos (armazenamento) para reais (exibição).
     * Ex.: 1000 centavos -> 10.00.
     */
    public static double toReais(long amountInCents) {
        return BigDecimal.valueOf(amountInCents)
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
