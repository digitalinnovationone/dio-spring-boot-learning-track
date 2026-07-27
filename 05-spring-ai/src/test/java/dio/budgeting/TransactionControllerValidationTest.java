package dio.budgeting;

import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infrastructure.idempotency.AudioCommandIdempotencyService;
import dio.budgeting.infrastructure.idempotency.AudioCommandOperationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that POST /transactions rejects invalid payloads with 400 Bad
 * Request via Bean Validation, and that a valid payload still results in
 * 201 Created. No OpenAI call, no MySQL/Docker: same isolation strategy as
 * SwaggerDocumentationTest (JPA/DataSource autoconfiguration excluded,
 * TransactionRepository mocked, placeholder OpenAI API key only to satisfy
 * property resolution for Spring AI's autoconfiguration).
 *
 * No custom error body is asserted here: TASK-004 explicitly leaves a
 * standardized error response format for a later task.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.ai.openai.api-key=test-key-not-a-real-credential",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
        }
)
@AutoConfigureMockMvc
class TransactionControllerValidationTest {

    @MockitoBean
    TransactionRepository transactionRepository;

    @MockitoBean
    AudioCommandIdempotencyService idempotencyService;

    // Unused directly, but a real @Component that needs the JPA repository this
    // test's context excludes - must be mocked too so the context can start.
    @MockitoBean
    AudioCommandOperationStore audioCommandOperationStore;

    @Autowired
    MockMvc mockMvc;

    @Test
    void validPayload_isAccepted() throws Exception {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": 80.90,
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isCreated());
    }

    @Test
    void blankDescription_isRejectedWith400() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "",
                                  "amount": 80.90,
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isBadRequest());

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void nullAmount_isRejectedWith400() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isBadRequest());

        verify(transactionRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-10.00"})
    void nonPositiveAmount_isRejectedWith400(String amount) throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": %s,
                                  "category": "AUTO"
                                }""".formatted(amount)))
                .andExpect(status().isBadRequest());

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void nullCategory_isRejectedWith400() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": 80.90
                                }"""))
                .andExpect(status().isBadRequest());

        verify(transactionRepository, never()).save(any());
    }
}
