package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistTransactionUseCaseTest {

    private InMemoryTransactionRepository repository;
    private PersistTransactionUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        useCase = new PersistTransactionUseCase(repository);
    }

    @Test
    void devePersistirTransacaoValida() {
        var input = new PersistTransactionInput("Remédio", 4500, Category.PHARMA);

        var output = useCase.execute(input);

        assertNotNull(output.id());
        assertEquals("Remédio", output.description());
        assertEquals(Category.PHARMA.name(), output.category());
        assertEquals(1, repository.findAllByCategory(Category.PHARMA).size());
    }

    @Test
    void naoDevePersistirTransacaoComValorInvalido() {
        var input = new PersistTransactionInput("Remédio", -10, Category.PHARMA);

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(input));
        assertTrue(repository.findAllByCategory(Category.PHARMA).isEmpty());
    }

    /**
     * Fake em memória: evita a necessidade de um framework de mock e de contexto
     * Spring só para testar a regra de negócio do use case.
     */
    static class InMemoryTransactionRepository implements TransactionRepository {
        private final List<Transaction> transactions = new ArrayList<>();

        @Override
        public Transaction save(Transaction transaction) {
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public List<Transaction> findAllByCategory(Category category) {
            return transactions.stream().filter(t -> t.getCategory() == category).toList();
        }
    }
}