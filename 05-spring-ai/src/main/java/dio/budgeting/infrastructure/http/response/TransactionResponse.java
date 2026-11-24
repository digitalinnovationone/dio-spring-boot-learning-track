package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.TransactionOutput;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param amount valor em reais (ex.: 80.00)
 */
public record TransactionResponse(String id, String category, String description, BigDecimal amount, LocalDate date) {
    public static TransactionResponse from(TransactionOutput output) {
        return new TransactionResponse(output.id(), output.category(), output.description(), output.value(),
                output.date());
    }
}
