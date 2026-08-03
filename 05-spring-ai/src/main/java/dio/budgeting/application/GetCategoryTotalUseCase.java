package dio.budgeting.application;

import dio.budgeting.application.output.CategoryTotalOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class GetCategoryTotalUseCase {
    private final TransactionRepository transactionRepository;

    public GetCategoryTotalUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "total-by-category", description = "Calcula o total gasto em uma categoria financeira")
    public CategoryTotalOutput execute(@ToolParam(description = "Categoria de uma transação") Category category) {
        long totalInCents = transactionRepository.findAllByCategory(category).stream()
                .mapToLong(Transaction::getAmount)
                .sum();

        return CategoryTotalOutput.of(category, totalInCents);
    }
}
