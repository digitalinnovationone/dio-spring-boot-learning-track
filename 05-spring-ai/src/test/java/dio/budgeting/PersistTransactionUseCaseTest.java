package dio.budgeting;

import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.InvalidTransactionException;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link PersistTransactionUseCase} never persists an invalid
 * transaction: the domain rejects it before {@code TransactionRepository.save}
 * is ever invoked. No Spring context, no database.
 */
class PersistTransactionUseCaseTest {

    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final PersistTransactionUseCase useCase = new PersistTransactionUseCase(repository);

    @Test
    void validTransaction_isSavedExactlyOnce() {
        when(repository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var input = new PersistTransactionInput("Combustível", new BigDecimal("80.90"), Category.AUTO);
        var output = useCase.execute(input);

        verify(repository).save(any(Transaction.class));
        assertThat(output.value()).isEqualByComparingTo(new BigDecimal("80.90"));
    }

    @Test
    void description_isPersisted_withoutLeadingOrTrailingSpaces() {
        when(repository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var input = new PersistTransactionInput("  Combustível  ", new BigDecimal("80.90"), Category.AUTO);
        useCase.execute(input);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("Combustível");
    }

    @Test
    void amount_isPersisted_normalizedToTwoDecimals() {
        when(repository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var input = new PersistTransactionInput("Combustível", new BigDecimal("80.905"), Category.AUTO);
        useCase.execute(input);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("80.91"));
    }

    @Test
    void zeroAmount_neverCallsSave() {
        var input = new PersistTransactionInput("Combustível", BigDecimal.ZERO, Category.AUTO);

        assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(InvalidTransactionException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void negativeAmount_neverCallsSave() {
        var input = new PersistTransactionInput("Combustível", new BigDecimal("-10.00"), Category.AUTO);

        assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(InvalidTransactionException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void blankDescription_neverCallsSave() {
        var input = new PersistTransactionInput("   ", new BigDecimal("10.00"), Category.AUTO);

        assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(InvalidTransactionException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void nullCategory_neverCallsSave() {
        var input = new PersistTransactionInput("Combustível", new BigDecimal("10.00"), null);

        assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(InvalidTransactionException.class);

        verify(repository, never()).save(any());
    }
}
