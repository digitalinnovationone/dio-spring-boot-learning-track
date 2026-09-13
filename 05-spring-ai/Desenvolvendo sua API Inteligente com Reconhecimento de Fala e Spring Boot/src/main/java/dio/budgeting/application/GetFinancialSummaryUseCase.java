package dio.budgeting.application;

import dio.budgeting.application.output.FinancialSummaryOutput;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GetFinancialSummaryUseCase {
    private final TransactionRepository transactionRepository;

    public GetFinancialSummaryUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "get-financial-summary", description = "Gera e retorna um resumo financeiro com total gasto em Reais, quantidade de transações, média e totais por categoria")
    public FinancialSummaryOutput execute() {
        List<Transaction> transactions = transactionRepository.findAll();

        if (transactions.isEmpty()) {
            return new FinancialSummaryOutput(0.0, 0, 0.0, 0.0, Map.of());
        }

        double totalSpent = transactions.stream()
                .mapToDouble(t -> t.getAmount() / 100.0)
                .sum();

        long count = transactions.size();

        double averageSpent = count > 0 ? totalSpent / count : 0.0;

        double maxSpent = transactions.stream()
                .mapToDouble(t -> t.getAmount() / 100.0)
                .max()
                .orElse(0.0);

        Map<String, Double> byCategory = transactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCategory().name(),
                        Collectors.summingDouble(t -> t.getAmount() / 100.0)
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> round(e.getValue())
                ));

        return new FinancialSummaryOutput(
                round(totalSpent),
                count,
                round(averageSpent),
                round(maxSpent),
                byCategory
        );
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
