package dio.budgeting.domain;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class Transaction {
    public static final int MAX_DESCRIPTION_LENGTH = 255;

    private final TransactionId id;
    private final String description;
    private final long amount;
    private final Category category;
    private final LocalDate date;

    public Transaction(TransactionId id, String description, long amount, Category category, LocalDate date) {
        if (id == null) {
            throw new InvalidTransactionException("O identificador da transação é obrigatório");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidTransactionException("A descrição da transação é obrigatória");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidTransactionException(
                    "A descrição deve ter no máximo " + MAX_DESCRIPTION_LENGTH + " caracteres");
        }
        if (amount <= 0) {
            throw new InvalidTransactionException("O valor da transação deve ser maior que zero");
        }
        if (category == null) {
            throw new InvalidTransactionException("A categoria da transação é obrigatória");
        }
        if (date == null) {
            throw new InvalidTransactionException("A data da transação é obrigatória");
        }
        this.id = id;
        this.description = description.strip();
        this.amount = amount;
        this.category = category;
        this.date = date;
    }

    public Transaction(String description, long amount, Category category, LocalDate date) {
        this(new TransactionId(), description, amount, category, date);
    }
}
