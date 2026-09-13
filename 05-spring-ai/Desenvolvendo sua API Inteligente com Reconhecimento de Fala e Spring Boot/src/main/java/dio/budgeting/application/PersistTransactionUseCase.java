package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class PersistTransactionUseCase {
    private final TransactionRepository transactionRepository;

    public PersistTransactionUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "persist-transaction", description = "Persiste uma nova transação financeira")
    public TransactionOutput execute(PersistTransactionInput input) {
        if (input.amount() <= 0) {
            throw new IllegalArgumentException("O valor da transação deve ser maior que zero.");
        }
        if (input.description() == null || input.description().isBlank()) {
            throw new IllegalArgumentException("A descrição da transação é obrigatória.");
        }
        var category = input.category() != null ? input.category() : dio.budgeting.domain.Category.OTHER;

        var transaction = transactionRepository.save(
                new Transaction(input.description().trim(), input.amount(), category));

        return TransactionOutput.from(transaction);
    }
}
