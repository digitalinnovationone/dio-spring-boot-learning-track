package dio.budgeting.application.output;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SpendingSummaryOutput(LocalDate start,
                                    LocalDate end,
                                    int count,
                                    BigDecimal total,
                                    List<CategoryTotalOutput> byCategory,
                                    List<TransactionOutput> transactions) {
}
