package dio.budgeting.application.output;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;

import java.math.BigDecimal;
import java.util.List;

public record CategoryTotalOutput(String category, int count, BigDecimal total) {
    public static CategoryTotalOutput from(Category category, List<Transaction> transactions) {
        var cents = transactions.stream().mapToLong(Transaction::getAmount).sum();
        return new CategoryTotalOutput(category.name(), transactions.size(), Money.fromCents(cents));
    }
}
