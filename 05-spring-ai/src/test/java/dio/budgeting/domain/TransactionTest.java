package dio.budgeting.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    @Test
    void createsTransactionWhenDataIsValid() {
        var transaction = new Transaction("Mercado", 1500, Category.GROCERIES);

        assertThat(transaction.getId()).isNotNull();
        assertThat(transaction.getDescription()).isEqualTo("Mercado");
        assertThat(transaction.getAmount()).isEqualTo(1500);
        assertThat(transaction.getCategory()).isEqualTo(Category.GROCERIES);
    }

    @Test
    void rejectsBlankDescription() {
        assertThatThrownBy(() -> new Transaction("   ", 1500, Category.GROCERIES))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("descrição");
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> new Transaction("Mercado", 0, Category.GROCERIES))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("valor");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> new Transaction("Mercado", -100, Category.GROCERIES))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void rejectsNullCategory() {
        assertThatThrownBy(() -> new Transaction("Mercado", 1500, null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("categoria");
    }
}
