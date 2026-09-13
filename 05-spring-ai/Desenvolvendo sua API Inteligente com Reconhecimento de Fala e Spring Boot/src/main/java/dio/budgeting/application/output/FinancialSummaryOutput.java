package dio.budgeting.application.output;

import java.util.Map;

public record FinancialSummaryOutput(
        double totalSpent,
        long totalTransactions,
        double averageSpent,
        double maxSingleSpent,
        Map<String, Double> totalByCategory
) {
}
