package dio.budgeting.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    @Test
    void shouldCreateTransactionSuccessfully() {
        var userId = new UserId();
        var transaction = new Transaction(userId, "Supermarket shopping", 5000L, Category.GROCERIES);

        assertThat(transaction.getId()).isNotNull();
        assertThat(transaction.getUserId()).isEqualTo(userId);
        assertThat(transaction.getDescription()).isEqualTo("Supermarket shopping");
        assertThat(transaction.getAmount()).isEqualTo(5000L);
        assertThat(transaction.getCategory()).isEqualTo(Category.GROCERIES);
    }

    @Test
    void shouldThrowExceptionWhenUserIdIsNull() {
        assertThatThrownBy(() -> new Transaction(null, "Test", 1000L, Category.AUTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UserId cannot be null");
    }
}
