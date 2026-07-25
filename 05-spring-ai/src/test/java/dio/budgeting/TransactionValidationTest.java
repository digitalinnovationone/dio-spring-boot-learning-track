package dio.budgeting;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.InvalidTransactionException;
import dio.budgeting.domain.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the business invariants enforced by {@link Transaction}.
 * No Spring context, no repository, no I/O: pure domain tests.
 */
class TransactionValidationTest {

    // --- Valid scenarios ---

    @Test
    void createsTransaction_withNormalDescription() {
        var transaction = new Transaction("Combustível", new BigDecimal("80.90"), Category.AUTO);

        assertThat(transaction.getDescription()).isEqualTo("Combustível");
        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal("80.90"));
        assertThat(transaction.getCategory()).isEqualTo(Category.AUTO);
    }

    @Test
    void stripsLeadingAndTrailingSpaces_fromDescription() {
        var transaction = new Transaction("  Combustível  ", new BigDecimal("80.90"), Category.AUTO);

        assertThat(transaction.getDescription()).isEqualTo("Combustível");
    }

    @Test
    void normalizesAmount_fromHalfCentUp_toOneCent() {
        var transaction = new Transaction("Combustível", new BigDecimal("0.005"), Category.AUTO);

        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void acceptsEveryCategory_currentlyDefinedInTheEnum(Category category) {
        var transaction = new Transaction("Combustível", new BigDecimal("10.00"), category);

        assertThat(transaction.getCategory()).isEqualTo(category);
    }

    // --- Invalid: description ---

    @Test
    void rejectsNullDescription() {
        assertThatThrownBy(() -> new Transaction(null, new BigDecimal("10.00"), Category.AUTO))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("A descrição da transação não pode estar vazia.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void rejectsEmptyOrBlankDescription(String blankDescription) {
        assertThatThrownBy(() -> new Transaction(blankDescription, new BigDecimal("10.00"), Category.AUTO))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("A descrição da transação não pode estar vazia.");
    }

    // --- Invalid: amount ---

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Transaction("Combustível", null, Category.AUTO))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("O valor da transação é obrigatório.");
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> new Transaction("Combustível", BigDecimal.ZERO, Category.AUTO))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("O valor da transação deve ser maior que zero.");
    }

    @Test
    void rejectsZeroAmount_withExplicitScale() {
        assertThatThrownBy(() -> new Transaction("Combustível", new BigDecimal("0.00"), Category.AUTO))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("O valor da transação deve ser maior que zero.");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> new Transaction("Combustível", new BigDecimal("-10.00"), Category.AUTO))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("O valor da transação deve ser maior que zero.");
    }

    @Test
    void rejectsAmount_thatNormalizesToZero_belowHalfCent() {
        // 0.001 rounds (HALF_UP) to 0.00, which must be treated as invalid:
        // positivity is validated on the normalized (actually stored) value, not the raw input.
        assertThatThrownBy(() -> new Transaction("Combustível", new BigDecimal("0.001"), Category.AUTO))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("O valor da transação deve ser maior que zero.");
    }

    @Test
    void rejectsAmount_thatNormalizesToZero_atHalfCentBoundaryRoundingDown() {
        // 0.004 rounds (HALF_UP) to 0.00 as well.
        assertThatThrownBy(() -> new Transaction("Combustível", new BigDecimal("0.004"), Category.AUTO))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("O valor da transação deve ser maior que zero.");
    }

    @Test
    void acceptsAmount_thatNormalizesAboveZero_atHalfCentBoundaryRoundingUp() {
        // 0.005 rounds (HALF_UP) to 0.01, which is valid.
        var transaction = new Transaction("Combustível", new BigDecimal("0.005"), Category.AUTO);

        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    // --- Invalid: category ---

    @Test
    void rejectsNullCategory() {
        assertThatThrownBy(() -> new Transaction("Combustível", new BigDecimal("10.00"), null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("A categoria da transação é obrigatória.");
    }
}
