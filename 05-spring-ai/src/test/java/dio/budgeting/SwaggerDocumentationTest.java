package dio.budgeting;

import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infrastructure.idempotency.AudioCommandIdempotencyService;
import dio.budgeting.infrastructure.idempotency.AudioCommandOperationStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization tests for the springdoc-openapi wiring only.
 *
 * No call to OpenAI, no MySQL/Docker: the JPA/DataSource auto-configuration is
 * excluded and {@link TransactionRepository} is mocked, so persistence is never
 * exercised. Spring AI's {@code ChatClient}/{@code TranscriptionModel}/
 * {@code TextToSpeechModel} beans are still created by their own
 * auto-configuration (that's how the real app wires {@code TransactionController}),
 * but only client objects are built — no network call happens during context
 * startup, so a placeholder API key is enough to satisfy property resolution.
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
class SwaggerDocumentationTest {

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
    void apiDocsEndpoint_respondsSuccessfully() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void apiDocs_containsExpectedApiTitleAndVersion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.info.title").value("Budgeting API"))
                .andExpect(jsonPath("$.info.version").value("0.0.1-SNAPSHOT"));
    }

    @Test
    void apiDocs_documentsAllThreeTransactionEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths./transactions.post").exists())
                .andExpect(jsonPath("$.paths./transactions/{category}.get").exists())
                .andExpect(jsonPath("$.paths./transactions/ai.post").exists());
    }

    @Test
    void aiEndpoint_isDocumentedAsMultipartUpload() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths./transactions/ai.post.requestBody.content.['multipart/form-data']").exists());
    }

    @Test
    void postTransactions_documentsCreatedResponse() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths./transactions.post.responses.201").exists());
    }

    @Test
    void postTransactions_documentsBadRequestResponse() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths./transactions.post.responses.400").exists());
    }

    @Test
    void transactionRequestSchema_marksDescriptionCategoryAndAmount_asRequired() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.TransactionRequest.required")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("description", "category", "amount")));
    }

    @Test
    void amountSchema_isDocumentedAsDecimalNumber_notInteger() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.TransactionRequest.properties.amount.type").value("number"))
                .andExpect(jsonPath("$.components.schemas.TransactionResponse.properties.amount.type").value("number"));
    }

    @Test
    void postTransactionsExample_usesDecimalAmount() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("80.9")));
    }

    @Test
    void getTransactionsByCategory_documentsCategoryPathParameter() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths./transactions/{category}.get.parameters[0].name").value("category"));
    }

    @Test
    void swaggerUiIndex_isServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void aiEndpoint_filePart_isMarkedRequired() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(
                        "$.paths./transactions/ai.post.requestBody.content.['multipart/form-data'].schema.required")
                        .value(org.hamcrest.Matchers.hasItem("file")));
    }

    @Test
    void aiEndpoint_describesAcceptedFormatsAndSizeLimit() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(
                        "$.paths./transactions/ai.post.requestBody.content.['multipart/form-data'].schema.properties.file.description")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("audio/mpeg"),
                                org.hamcrest.Matchers.containsString("10 MB"))));
    }

    @Test
    void aiEndpoint_documentsBadRequestResponse() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths./transactions/ai.post.responses.400").exists());
    }

    @Test
    void aiEndpoint_documentsPayloadTooLargeResponse() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths./transactions/ai.post.responses.413").exists());
    }

    @Test
    void aiEndpoint_documentsInternalServerErrorResponse() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths./transactions/ai.post.responses.500").exists());
    }

    @Test
    void aiEndpoint_successResponse_isStillAudioMp3() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths./transactions/ai.post.responses.200.content.['audio/mp3']").exists());
    }
}
