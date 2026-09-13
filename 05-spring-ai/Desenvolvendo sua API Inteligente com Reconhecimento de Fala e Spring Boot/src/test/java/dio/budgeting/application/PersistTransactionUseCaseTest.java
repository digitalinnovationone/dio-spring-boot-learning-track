package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersistTransactionUseCaseTest {

    private TransactionRepository transactionRepository;
    private PersistTransactionUseCase useCase;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        useCase = new PersistTransactionUseCase(transactionRepository);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o valor for menor ou igual a zero")
    void shouldThrowExceptionWhenAmountIsInvalid() {
        var input = new PersistTransactionInput("Almoço", 0, Category.FOOD);

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(input));
    }

    @Test
    @DisplayName("Deve lançar exceção quando a descrição for vazia")
    void shouldThrowExceptionWhenDescriptionIsBlank() {
        var input = new PersistTransactionInput("", 1000, Category.FOOD);

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(input));
    }

    @Test
    @DisplayName("Deve salvar transação com sucesso para entradas válidas")
    void shouldSaveTransactionSuccessfully() {
        var input = new PersistTransactionInput("Jantar", 2500, Category.FOOD);
        var expectedTransaction = new Transaction("Jantar", 2500, Category.FOOD);

        when(transactionRepository.save(any(Transaction.class))).thenReturn(expectedTransaction);

        var output = useCase.execute(input);

        assertNotNull(output);
        assertEquals("Jantar", output.description());
        assertEquals("FOOD", output.category());
        assertEquals(2500.0, output.value());
    }
}
