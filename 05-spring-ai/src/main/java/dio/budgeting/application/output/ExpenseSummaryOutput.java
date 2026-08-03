package dio.budgeting.application.output;

import java.util.Map;

/**
 * Resumo consolidado de gastos: total (em reais) por categoria e total geral.
 */
public record ExpenseSummaryOutput(Map<String, Double> totalsByCategory, double total) {
}
