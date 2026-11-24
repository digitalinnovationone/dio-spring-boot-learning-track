package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;

import java.time.LocalDate;

/**
 * @param amount valor em centavos (ex.: 8000 = R$ 80,00)
 * @param date   opcional; quando ausente, usa a data de hoje
 */
public record TransactionRequest(String description, Category category, long amount, LocalDate date) {
    public PersistTransactionInput toInput() {
        return new PersistTransactionInput(description, amount, category, date);
    }
}
