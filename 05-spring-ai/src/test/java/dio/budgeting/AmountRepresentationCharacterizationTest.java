package dio.budgeting;

import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization tests only. They document the CURRENT behavior of the
 * `amount` field (no unit conversion is applied anywhere in the code).
 * No production code is modified or corrected here — see TASK-001 report.
 */
class AmountRepresentationCharacterizationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- TransactionOutput.from: does it divide by 100? ---

    @Test
    void transactionOutput_doesNotDivideByOneHundred_forWholeAmount() {
        var transaction = new Transaction("Combustivel", 80L, Category.AUTO);

        var output = TransactionOutput.from(transaction);

        // If 80 meant "80 reais", value should stay 80.0.
        // If 80 meant "80 cents", value should have become 0.8 — it did not.
        assertThat(output.value()).isEqualTo(80.0);
    }

    @Test
    void transactionOutput_doesNotDivideByOneHundred_forEightThousand() {
        var transaction = new Transaction("Combustivel", 8000L, Category.AUTO);

        var output = TransactionOutput.from(transaction);

        // If 8000 meant "8000 cents" (i.e. R$ 80,00), a correct reais amount
        // would be 80.0. The actual current output is 8000.0 — no /100 applied.
        assertThat(output.value()).isEqualTo(8000.0);
    }

    @Test
    void transactionOutput_preservesFractionalCentsScale_whenAmountIs8090() {
        var transaction = new Transaction("Combustivel", 8090L, Category.AUTO);

        var output = TransactionOutput.from(transaction);

        // R$ 80,90 in cents (8090) is rendered back as 8090.0, not 80.90.
        assertThat(output.value()).isEqualTo(8090.0);
    }

    // --- Domain <-> JPA entity round trip ---

    @Test
    void domainToEntityToDomain_preservesAmountExactly() {
        var original = new Transaction("Combustivel", 8090L, Category.AUTO);

        var entity = TransactionEntity.from(original);
        assertThat(entity.getAmount()).isEqualTo(8090L);

        var roundTripped = entity.toDomain();
        assertThat(roundTripped.getAmount()).isEqualTo(original.getAmount());
    }

    // --- REST request deserialization ---

    @Test
    void transactionRequest_deserializes_integerAmount_asIs() throws Exception {
        String json = """
                {"description":"Combustivel","category":"AUTO","amount":80}
                """;

        var request = objectMapper.readValue(json, TransactionRequest.class);

        assertThat(request.amount()).isEqualTo(80L);
    }

    @Test
    void transactionRequest_deserializes_largeIntegerAmount_asIs() throws Exception {
        String json = """
                {"description":"Combustivel","category":"AUTO","amount":8000}
                """;

        var request = objectMapper.readValue(json, TransactionRequest.class);

        assertThat(request.amount()).isEqualTo(8000L);
    }

    @Test
    void transactionRequest_silentlyTruncates_decimalAmount_becauseFieldIsLong() {
        String json = """
                {"description":"Combustivel","category":"AUTO","amount":80.90}
                """;

        // Characterization result: with the Jackson 3.x engine used by
        // Spring Boot 4 on this project, deserializing a fractional JSON
        // number (80.90) into a primitive `long` does NOT throw. It is
        // silently truncated to 80 — the ".90" (i.e. 90 cents, if the
        // field is meant to be reais) is silently discarded.
        var request = objectMapper.readValue(json, TransactionRequest.class);

        assertThat(request.amount()).isEqualTo(80L);
    }

    // --- Use case: value actually handed to the repository ---

    @Test
    void persistTransactionUseCase_savesAmount_withoutAnyConversion() {
        var repository = mock(TransactionRepository.class);
        when(repository.save(org.mockito.ArgumentMatchers.any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var useCase = new PersistTransactionUseCase(repository);
        var input = new PersistTransactionInput("Combustivel", 8090L, Category.AUTO);

        var output = useCase.execute(input);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).save(captor.capture());

        // The raw long flows unchanged: input -> domain -> repository.save(...)
        assertThat(captor.getValue().getAmount()).isEqualTo(8090L);
        // ...and the same unconverted value comes back out in the tool/use-case output.
        assertThat(output.value()).isEqualTo(8090.0);
    }
}
