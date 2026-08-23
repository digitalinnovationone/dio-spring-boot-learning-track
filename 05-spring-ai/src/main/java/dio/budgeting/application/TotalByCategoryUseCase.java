package dio.budgeting.application;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class TotalByCategoryUseCase {

    private final TransactionRepository transactionRepository;

    public TotalByCategoryUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(
            name = "total-transactions-by-category",
            description = "Calcula o total gasto em uma categoria financeira"
    )
    public long execute(
            @ToolParam(description = "Categoria das transações")
            Category category
    ) {
        return transactionRepository
                .findAllByCategory(category)
                .stream()
                .mapToLong(Transaction::getAmount)
                .sum();
    }
}
