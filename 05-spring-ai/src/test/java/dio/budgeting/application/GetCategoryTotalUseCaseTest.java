package dio.budgeting.application;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GetCategoryTotalUseCaseTest {
    private InMemoryTransactionRepository repository;
    private GetCategoryTotalUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        useCase = new GetCategoryTotalUseCase(repository);
    }

    @Test
    void sumsAmountsOfCategoryInReais() {
        repository.save(new Transaction("Mercado", 1000, Category.GROCERIES));
        repository.save(new Transaction("Feira", 2550, Category.GROCERIES));
        repository.save(new Transaction("Farmácia", 5000, Category.PHARMA));

        var output = useCase.execute(Category.GROCERIES);

        assertThat(output.category()).isEqualTo("GROCERIES");
        assertThat(output.total()).isEqualTo(35.50);
    }

    @Test
    void returnsZeroWhenCategoryHasNoTransactions() {
        var output = useCase.execute(Category.AUTO);

        assertThat(output.total()).isEqualTo(0.0);
    }
}
