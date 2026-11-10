package dio.budgeting;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionValidationTest {
    @Test
    void shouldRejectBlankDescription() {
        assertThatThrownBy(() -> new Transaction(" ", 100, Category.GROCERIES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Descrição é obrigatória");
    }

    @Test
    void shouldRejectDescriptionLongerThan255Characters() {
        var description = "a".repeat(256);

        assertThatThrownBy(() -> new Transaction(description, 100, Category.GROCERIES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Descrição deve ter no máximo 255 caracteres");
    }

    @Test
    void shouldRejectNonPositiveAmount() {
        assertThatThrownBy(() -> new Transaction("Mercado", 0, Category.GROCERIES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valor deve ser maior que zero");
    }

    @Test
    void shouldRejectNullCategory() {
        assertThatThrownBy(() -> new Transaction("Mercado", 100, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Categoria é obrigatória");
    }
}
