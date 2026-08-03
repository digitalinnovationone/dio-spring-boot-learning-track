package dio.budgeting.application;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SummarizeExpensesUseCaseTest {
    private InMemoryTransactionRepository repository;
    private SummarizeExpensesUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        useCase = new SummarizeExpensesUseCase(repository);
    }

    @Test
    void summarizesTotalsByCategoryAndGrandTotal() {
        repository.save(new Transaction("Mercado", 1000, Category.GROCERIES));
        repository.save(new Transaction("Feira", 2000, Category.GROCERIES));
        repository.save(new Transaction("Farmácia", 5000, Category.PHARMA));

        var output = useCase.execute();

        assertThat(output.totalsByCategory())
                .containsEntry("GROCERIES", 30.00)
                .containsEntry("PHARMA", 50.00)
                .containsEntry("AUTO", 0.00);
        assertThat(output.total()).isEqualTo(80.00);
    }

    @Test
    void reportsEveryCategoryAsZeroWhenThereAreNoTransactions() {
        var output = useCase.execute();

        assertThat(output.total()).isEqualTo(0.0);
        assertThat(output.totalsByCategory()).containsOnlyKeys("GROCERIES", "PHARMA", "AUTO");
        assertThat(output.totalsByCategory().values()).allMatch(value -> value == 0.0);
    }
}
