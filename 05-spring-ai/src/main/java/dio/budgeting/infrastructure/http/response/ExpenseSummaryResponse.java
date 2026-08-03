package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.ExpenseSummaryOutput;

import java.util.Map;

public record ExpenseSummaryResponse(Map<String, Double> totalsByCategory, double total) {
    public static ExpenseSummaryResponse from(ExpenseSummaryOutput output) {
        return new ExpenseSummaryResponse(output.totalsByCategory(), output.total());
    }
}
