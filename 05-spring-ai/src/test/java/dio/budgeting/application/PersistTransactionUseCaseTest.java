package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.InvalidTransactionException;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistTransactionUseCaseTest {
    private InMemoryTransactionRepository repository;
    private PersistTransactionUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        useCase = new PersistTransactionUseCase(repository);
    }

    @Test
    void persistsValidTransactionAndConvertsCentsToReais() {
        var output = useCase.execute(new PersistTransactionInput("Farmácia", 2599, Category.PHARMA));

        assertThat(output.description()).isEqualTo("Farmácia");
        assertThat(output.category()).isEqualTo("PHARMA");
        assertThat(output.value()).isEqualTo(25.99);
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void rejectsInvalidTransactionFromIaOrRest() {
        assertThatThrownBy(() -> useCase.execute(new PersistTransactionInput("Farmácia", 0, Category.PHARMA)))
                .isInstanceOf(InvalidTransactionException.class);

        assertThat(repository.findAll()).isEmpty();
    }
}
