package dio.budgeting.domain;

import lombok.Getter;

@Getter
public class Transaction {
    private final TransactionId id;
    private final String description;
    private final long amount;
    private final Category category;

    /**
     * Reconstrói uma transação já existente (ex.: a partir da persistência).
     * Não revalida invariantes: representa dados previamente aceitos.
     */
    public Transaction(TransactionId id, String description, long amount, Category category) {
        this.id = id;
        this.description = description;
        this.amount = amount;
        this.category = category;
    }

    /**
     * Cria uma nova transação, garantindo as invariantes do domínio.
     * Vale para qualquer ponto de entrada (REST ou Tool Calling da IA).
     */
    public Transaction(String description, long amount, Category category) {
        validate(description, amount, category);
        this.id = new TransactionId();
        this.description = description;
        this.amount = amount;
        this.category = category;
    }

    private static void validate(String description, long amount, Category category) {
        if (description == null || description.isBlank()) {
            throw new InvalidTransactionException("A descrição da transação é obrigatória.");
        }
        if (amount <= 0) {
            throw new InvalidTransactionException("O valor da transação deve ser maior que zero (em centavos).");
        }
        if (category == null) {
            throw new InvalidTransactionException("A categoria da transação é obrigatória.");
        }
    }
}
