package dio.budgeting.application;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetSpendingSummaryUseCaseTest {
    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 30);
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final GetSpendingSummaryUseCase useCase = new GetSpendingSummaryUseCase(repository);

    @Test
    void should_summarizeOnlyTransactionsInsideThePeriod() {
        repository.save(new Transaction("Mercado", 8000, Category.GROCERIES, START));
        repository.save(new Transaction("Restaurante", 12000, Category.RESTAURANT, END));
        repository.save(new Transaction("Mercado", 2000, Category.GROCERIES, LocalDate.of(2026, 11, 15)));
        repository.save(new Transaction("Fora do período", 99900, Category.SHOPPING, END.plusDays(1)));

        var summary = useCase.execute(START, END);

        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.total()).isEqualByComparingTo(new BigDecimal("220.00"));
        assertThat(summary.byCategory())
                .extracting("category", "count")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("RESTAURANT", 1),
                        org.assertj.core.groups.Tuple.tuple("GROCERIES", 2));
    }

    @Test
    void should_reject_when_startIsAfterEnd() {
        assertThatThrownBy(() -> useCase.execute(END, START))
                .isInstanceOf(InvalidPeriodException.class);
    }

    @Test
    void should_returnEmptySummary_when_thereAreNoTransactions() {
        var summary = useCase.execute(START, END);

        assertThat(summary.count()).isZero();
        assertThat(summary.total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.byCategory()).isEmpty();
    }
}
