package dio.budgeting.application;

import dio.budgeting.application.output.ExpenseSummaryOutput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SummarizeExpensesUseCase {
    private final TransactionRepository transactionRepository;

    public SummarizeExpensesUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "summarize-expenses",
            description = "Gera um resumo dos gastos com o total por categoria e o total geral")
    public ExpenseSummaryOutput execute() {
        List<Transaction> transactions = transactionRepository.findAll();

        Map<String, Double> totalsByCategory = new LinkedHashMap<>();
        long grandTotalInCents = 0L;

        for (Category category : Category.values()) {
            long categoryTotalInCents = transactions.stream()
                    .filter(transaction -> transaction.getCategory() == category)
                    .mapToLong(Transaction::getAmount)
                    .sum();

            totalsByCategory.put(category.name(), TransactionOutput.toReais(categoryTotalInCents));
            grandTotalInCents += categoryTotalInCents;
        }

        return new ExpenseSummaryOutput(totalsByCategory, TransactionOutput.toReais(grandTotalInCents));
    }
}
