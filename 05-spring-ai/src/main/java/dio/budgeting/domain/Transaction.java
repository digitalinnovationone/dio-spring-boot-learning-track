package dio.budgeting.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Transaction {
    private TransactionId id;
    private String description;
    private long amount;
    private Category category;

    public Transaction(String description, long amount, Category category) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Descrição é obrigatória");
        }
        if (description.length() > 255) {
            throw new IllegalArgumentException("Descrição deve ter no máximo 255 caracteres");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Valor deve ser maior que zero");
        }
        if (category == null) {
            throw new IllegalArgumentException("Categoria é obrigatória");
        }

        this.id = new TransactionId();
        this.description = description;
        this.amount = amount;
        this.category = category;
    }
}
