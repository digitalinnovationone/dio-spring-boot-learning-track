package dio.budgeting.application;

import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.TransactionType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class GetTotalExpensesUseCase {
    private final TransactionRepository transactionRepository;

    public GetTotalExpensesUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "get-total-expenses", description = "Calcula o valor total de todos os gastos registrados (em centavos)")
    public long execute() {
        return transactionRepository.findAll()
                .stream()
                .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
                .mapToLong(Transaction::getAmount)
                .sum();
    }
}