package dio.budgeting;

import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Replaces {@code AmountRepresentationCharacterizationTest}, which documented
 * the previous (incorrect) `long`-based behavior of the `amount` field: no
 * unit conversion, silent truncation of decimals on REST deserialization, and
 * a formatting-only conversion in {@code TransactionOutput}. That behavior no
 * longer exists after the migration to {@code BigDecimal}, so those tests
 * would either fail or assert the wrong thing going forward — hence the
 * replacement instead of an update in place. This class verifies the new,
 * correct behavior instead: no OpenAI, MySQL or Docker involved.
 */
class MonetaryAmountBigDecimalTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- Normalization performed by the domain (Transaction) ---

    @ParameterizedTest
    @CsvSource({
            "80,      80.00",
            "80.9,    80.90",
            "80.901,  80.90",
            "80.905,  80.91",
    })
    void transaction_normalizesAmountToTwoDecimals_withHalfUpRounding(String input, String expected) {
        var transaction = new Transaction("Combustivel", new BigDecimal(input), Category.AUTO);

        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal(expected));
        assertThat(transaction.getAmount().scale()).isEqualTo(2);
    }

    // --- Domain <-> JPA entity round trip ---

    @Test
    void domainToEntityToDomain_preservesNormalizedAmountExactly() {
        var original = new Transaction("Combustivel", new BigDecimal("80.90"), Category.AUTO);

        var entity = TransactionEntity.from(original);
        assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("80.90"));

        var roundTripped = entity.toDomain();
        assertThat(roundTripped.getAmount()).isEqualByComparingTo(original.getAmount());
    }

    // --- REST request deserialization: no more silent truncation ---

    @Test
    void transactionRequest_deserializes_decimalAmount_withoutTruncation() {
        String json = """
                {"description":"Combustivel","category":"AUTO","amount":80.90}
                """;

        var request = objectMapper.readValue(json, TransactionRequest.class);

        assertThat(request.amount()).isEqualByComparingTo(new BigDecimal("80.90"));
    }

    @Test
    void transactionRequest_deserializes_integerAmount_asWholeReais() {
        String json = """
                {"description":"Combustivel","category":"AUTO","amount":80}
                """;

        var request = objectMapper.readValue(json, TransactionRequest.class);

        assertThat(request.amount()).isEqualByComparingTo(new BigDecimal("80"));
    }

    // --- Use case: value actually handed to the repository, without loss ---

    @Test
    void persistTransactionUseCase_savesAndReturnsAmount_withoutPrecisionLoss() {
        var repository = mock(TransactionRepository.class);
        when(repository.save(org.mockito.ArgumentMatchers.any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var useCase = new PersistTransactionUseCase(repository);
        var input = new PersistTransactionInput("Combustivel", new BigDecimal("80.90"), Category.AUTO);

        var output = useCase.execute(input);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("80.90"));
        assertThat(output.value()).isEqualByComparingTo(new BigDecimal("80.90"));
    }
}
