package dio.budgeting.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @Test
    void should_createTransaction_when_dataIsValid() {
        var transaction = new Transaction("  Mercado  ", 8000, Category.GROCERIES, TODAY);

        assertThat(transaction.getId()).isNotNull();
        assertThat(transaction.getDescription()).isEqualTo("Mercado");
        assertThat(transaction.getAmount()).isEqualTo(8000);
        assertThat(transaction.getDate()).isEqualTo(TODAY);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -8000})
    void should_reject_when_amountIsNotPositive(long amount) {
        assertThatThrownBy(() -> new Transaction("Mercado", amount, Category.GROCERIES, TODAY))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("maior que zero");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void should_reject_when_descriptionIsBlank(String description) {
        assertThatThrownBy(() -> new Transaction(description, 8000, Category.GROCERIES, TODAY))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("descrição");
    }

    @Test
    void should_reject_when_descriptionIsTooLong() {
        var description = "a".repeat(Transaction.MAX_DESCRIPTION_LENGTH + 1);

        assertThatThrownBy(() -> new Transaction(description, 8000, Category.GROCERIES, TODAY))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void should_reject_when_categoryOrDateIsMissing() {
        assertThatThrownBy(() -> new Transaction("Mercado", 8000, null, TODAY))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("categoria");
        assertThatThrownBy(() -> new Transaction("Mercado", 8000, Category.GROCERIES, null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("data");
    }
}
