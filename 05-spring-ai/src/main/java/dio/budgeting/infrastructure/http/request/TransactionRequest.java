package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionType;

public record TransactionRequest(String description, Category category, long amount, TransactionType type) {
    public PersistTransactionInput toInput() {
        return new PersistTransactionInput(description, amount, category, type != null ? type : TransactionType.EXPENSE);
    }
}
