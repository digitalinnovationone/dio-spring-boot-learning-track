package dio.budgeting.application.output;

import dio.budgeting.domain.Category;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CategoryTotalOutput(Category category, double total, int transactionCount) {
    public static CategoryTotalOutput from(Category category, long amountInCents, int transactionCount) {
        return new CategoryTotalOutput(
                category,
                BigDecimal.valueOf(amountInCents).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                transactionCount);
    }
}
