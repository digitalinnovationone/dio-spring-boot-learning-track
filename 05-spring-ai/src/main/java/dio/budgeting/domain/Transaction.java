package dio.budgeting.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
public class Transaction {
    private TransactionId id;
    private String description;
    private BigDecimal amount;
    private Category category;

    public Transaction(String description, BigDecimal amount, Category category) {
        this(new TransactionId(), description, amount, category);
    }

    public Transaction(TransactionId id, String description, BigDecimal amount, Category category) {
        if (amount == null) {
            throw new InvalidTransactionException("O valor da transação é obrigatório.");
        }
        var normalizedAmount = normalize(amount);
        if (normalizedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("O valor da transação deve ser maior que zero.");
        }

        if (description == null || description.isBlank()) {
            throw new InvalidTransactionException("A descrição da transação não pode estar vazia.");
        }
        var normalizedDescription = description.strip();

        if (category == null) {
            throw new InvalidTransactionException("A categoria da transação é obrigatória.");
        }

        this.id = id;
        this.description = normalizedDescription;
        this.amount = normalizedAmount;
        this.category = category;
    }

    private static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
