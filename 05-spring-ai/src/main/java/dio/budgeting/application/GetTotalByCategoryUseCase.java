package dio.budgeting.application;

import dio.budgeting.application.output.CategoryTotalOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class GetTotalByCategoryUseCase {
    private final TransactionRepository transactionRepository;

    public GetTotalByCategoryUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "get-total-by-category",
            description = "Calcula o total gasto (em reais) e a quantidade de transações de uma categoria")
    public CategoryTotalOutput execute(@ToolParam(description = "Categoria de uma transação") Category category) {
        return CategoryTotalOutput.from(category, transactionRepository.findAllByCategory(category));
    }
}
