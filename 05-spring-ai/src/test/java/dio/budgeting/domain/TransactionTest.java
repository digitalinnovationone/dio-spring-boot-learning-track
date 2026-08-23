package dio.budgeting.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionTest {

    @Test
    void deveCriarTransacaoValida() {
        var transaction = new Transaction("Supermercado", 15000, Category.GROCERIES);

        assertNotNull(transaction.getId());
        assertEquals("Supermercado", transaction.getDescription());
        assertEquals(15000, transaction.getAmount());
        assertEquals(Category.GROCERIES, transaction.getCategory());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void naoDeveCriarTransacaoComDescricaoInvalida(String descricaoInvalida) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new Transaction(descricaoInvalida, 1000, Category.GROCERIES));

        assertEquals("A descrição da transação não pode ser vazia", exception.getMessage());
    }

    @Test
    void naoDeveCriarTransacaoComValorZeroOuNegativo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction("Farmácia", 0, Category.PHARMA));

        assertThrows(IllegalArgumentException.class,
                () -> new Transaction("Farmácia", -100, Category.PHARMA));
    }

    @Test
    void naoDeveCriarTransacaoSemCategoria() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction("Posto de gasolina", 5000, null));
    }
}