package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistTransactionUseCaseTest {

    private final Category category = Category.values()[0];
    private TransactionRepository repository;
    private PersistTransactionUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(TransactionRepository.class);
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        useCase = new PersistTransactionUseCase(repository, validator);
    }

    @Test
    void deveSalvarQuandoOInputForValido() {
        when(repository.save(any(Transaction.class)))
                .thenReturn(new Transaction("Mercado", 5000, category));

        var output = useCase.execute(new PersistTransactionInput("Mercado", 5000, category));

        assertNotNull(output);
        verify(repository).save(any(Transaction.class));
    }

    @Test
    void naoDeveSalvarQuandoValorForZeroOuNegativo() {
        assertThrows(ConstraintViolationException.class,
                () -> useCase.execute(new PersistTransactionInput("Mercado", 0, category)));
        assertThrows(ConstraintViolationException.class,
                () -> useCase.execute(new PersistTransactionInput("Mercado", -100, category)));

        verify(repository, never()).save(any(Transaction.class));
    }

    @Test
    void naoDeveSalvarQuandoDescricaoEstiverEmBranco() {
        assertThrows(ConstraintViolationException.class,
                () -> useCase.execute(new PersistTransactionInput("  ", 5000, category)));

        verify(repository, never()).save(any(Transaction.class));
    }

    @Test
    void naoDeveSalvarQuandoCategoriaForNula() {
        assertThrows(ConstraintViolationException.class,
                () -> useCase.execute(new PersistTransactionInput("Mercado", 5000, null)));

        verify(repository, never()).save(any(Transaction.class));
    }
}
