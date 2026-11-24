package dio.budgeting.application;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class GetTotalByCategoryUseCaseTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final GetTotalByCategoryUseCase useCase = new GetTotalByCategoryUseCase(repository);

    @Test
    void should_sumOnlyTheRequestedCategory() {
        repository.save(new Transaction("Mercado A", 8000, Category.GROCERIES, DAY));
        repository.save(new Transaction("Mercado B", 4050, Category.GROCERIES, DAY));
        repository.save(new Transaction("Farmácia", 2000, Category.PHARMA, DAY));

        var output = useCase.execute(Category.GROCERIES);

        assertThat(output.category()).isEqualTo("GROCERIES");
        assertThat(output.count()).isEqualTo(2);
        assertThat(output.total()).isEqualByComparingTo(new BigDecimal("120.50"));
    }

    @Test
    void should_returnZero_when_categoryHasNoTransactions() {
        var output = useCase.execute(Category.LEISURE);

        assertThat(output.count()).isZero();
        assertThat(output.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
