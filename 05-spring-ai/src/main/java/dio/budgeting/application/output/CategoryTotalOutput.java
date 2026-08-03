package dio.budgeting.application.output;

import dio.budgeting.domain.Category;

/**
 * Total gasto (em reais) em uma categoria específica.
 */
public record CategoryTotalOutput(String category, double total) {
    public static CategoryTotalOutput of(Category category, long totalInCents) {
        return new CategoryTotalOutput(category.name(), TransactionOutput.toReais(totalInCents));
    }
}
