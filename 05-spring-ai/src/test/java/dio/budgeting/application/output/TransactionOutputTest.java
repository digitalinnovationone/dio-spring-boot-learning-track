package dio.budgeting.application.output;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionOutputTest {

    @Test
    void convertsCentsToReais() {
        assertThat(TransactionOutput.toReais(1000)).isEqualTo(10.00);
        assertThat(TransactionOutput.toReais(2599)).isEqualTo(25.99);
        assertThat(TransactionOutput.toReais(5)).isEqualTo(0.05);
        assertThat(TransactionOutput.toReais(0)).isEqualTo(0.0);
    }
}
