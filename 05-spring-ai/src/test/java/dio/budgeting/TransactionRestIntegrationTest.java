package dio.budgeting;

import com.jayway.jsonpath.JsonPath;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end REST integration tests: real controller, real use cases, real
 * Spring Data JPA repository, against an isolated H2 in-memory database
 * (profile "test", see application-test.properties). No MySQL, no Docker.
 * The OpenAI-backed beans (ChatClient, TranscriptionModel, TextToSpeechModel)
 * are real too - they are only constructed with a placeholder API key, which
 * existing tests in this module already demonstrate does not trigger network
 * calls at context startup. No OpenAI call happens in this class: the single
 * /transactions/ai test only exercises the pre-validation 400 path.
 *
 * @Transactional rolls back each test's changes, keeping tests independent
 * without manual cleanup; the H2 schema itself is recreated per test class
 * run via ddl-auto=create-drop.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionRestIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransactionEntityRepository transactionEntityRepository;

    // --- POST /transactions: valid creation ---

    @Test
    void createTransaction_valid_isPersistedAndReturned() throws Exception {
        var body = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": 80.90,
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.description").value("Combustível"))
                .andExpect(jsonPath("$.category").value("AUTO"))
                .andExpect(jsonPath("$.amount").value(80.90))
                .andReturn().getResponse().getContentAsString();

        var id = UUID.fromString(JsonPath.read(body, "$.id"));
        var entity = transactionEntityRepository.findById(id).orElseThrow();
        assertThat(entity.getDescription()).isEqualTo("Combustível");
        assertThat(entity.getCategory()).isEqualTo(Category.AUTO);
        assertThat(entity.getAmount()).isEqualByComparingTo("80.90");
        assertThat(entity.getAmount().scale()).isEqualTo(2);
    }

    @Test
    void createTransaction_normalizesDescriptionAndAmount_inResponseAndPersistence() throws Exception {
        var body = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "  Combustível  ",
                                  "amount": 80.905,
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Combustível"))
                .andExpect(jsonPath("$.amount").value(80.91))
                .andReturn().getResponse().getContentAsString();

        var id = UUID.fromString(JsonPath.read(body, "$.id"));
        var entity = transactionEntityRepository.findById(id).orElseThrow();
        assertThat(entity.getDescription()).isEqualTo("Combustível");
        assertThat(entity.getAmount()).isEqualByComparingTo("80.91");
    }

    @Test
    void createTransaction_integerAmount_isStoredWithoutPrecisionLoss() throws Exception {
        var body = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Mercado",
                                  "amount": 80,
                                  "category": "GROCERIES"
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(80))
                .andReturn().getResponse().getContentAsString();

        var id = UUID.fromString(JsonPath.read(body, "$.id"));
        var entity = transactionEntityRepository.findById(id).orElseThrow();
        assertThat(entity.getAmount()).isEqualByComparingTo("80.00");
    }

    // --- GET /transactions/{category} ---

    @Test
    void listByCategory_returnsOnlyMatchingTransactions() throws Exception {
        createViaHttp("Combustível", "80.90", Category.AUTO);
        createViaHttp("Estacionamento", "15.00", Category.AUTO);
        createViaHttp("Mercado", "120.50", Category.GROCERIES);
        createViaHttp("Medicamento", "35.75", Category.PHARMA);

        mockMvc.perform(get("/transactions/AUTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].category", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("AUTO"))))
                .andExpect(jsonPath("$[*].description", org.hamcrest.Matchers.containsInAnyOrder(
                        "Combustível", "Estacionamento")))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString()))));
    }

    @Test
    void listByCategory_noRecords_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/transactions/PHARMA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listByCategory_invalidCategory_returns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/transactions/FOOD"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.title").value("Parâmetro inválido"))
                .andExpect(jsonPath("$.detail").value(
                        "O valor 'FOOD' não é válido para o parâmetro 'category'. Valores aceitos: GROCERIES, PHARMA, AUTO."))
                .andExpect(jsonPath("$.instance").value("/transactions/FOOD"));
    }

    // --- Bean Validation / malformed input ---

    @Test
    void createTransaction_multipleInvalidFields_returns400_andPersistsNothing() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "",
                                  "amount": 0,
                                  "category": null
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.detail").value("Um ou mais campos possuem valores inválidos."))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.containsInAnyOrder(
                        "amount", "category", "description")));

        assertThat(transactionEntityRepository.count()).isZero();
    }

    @Test
    void createTransaction_malformedJson_returns400_andPersistsNothing() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": 80.90,
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value(
                        "O corpo da requisição está ausente ou possui formato inválido."));

        assertThat(transactionEntityRepository.count()).isZero();
    }

    @Test
    void createTransaction_nonNumericAmount_returns400_andPersistsNothing() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": "oitenta reais",
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"));

        assertThat(transactionEntityRepository.count()).isZero();
    }

    @Test
    void createTransaction_invalidCategoryInBody_returns400_andPersistsNothing() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": 80.90,
                                  "category": "FOOD"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value(
                        "Categoria inválida. Valores aceitos: GROCERIES, PHARMA, AUTO."));

        assertThat(transactionEntityRepository.count()).isZero();
    }

    // --- Rounding boundaries ---

    @Test
    void createTransaction_amountRoundsToZero_isRejectedByDomain_andPersistsNothing() throws Exception {
        // Bean Validation's @DecimalMin(0.00, exclusive) sees the raw 0.004 (> 0), so it
        // passes at the DTO layer; the domain then normalizes to 0.00 and rejects it.
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Ajuste",
                                  "amount": 0.004,
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Transação inválida"))
                .andExpect(jsonPath("$.detail").value("O valor da transação deve ser maior que zero."));

        assertThat(transactionEntityRepository.count()).isZero();
    }

    @Test
    void createTransaction_amountRoundsToOneCent_isPersisted() throws Exception {
        var body = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Ajuste",
                                  "amount": 0.005,
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(0.01))
                .andReturn().getResponse().getContentAsString();

        var id = UUID.fromString(JsonPath.read(body, "$.id"));
        var entity = transactionEntityRepository.findById(id).orElseThrow();
        assertThat(entity.getAmount()).isEqualByComparingTo("0.01");
    }

    // --- Isolation across a failed request followed by a valid one ---

    @Test
    void invalidRequestFollowedByValidRequest_leavesNoResidue() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "",
                                  "amount": 0,
                                  "category": null
                                }"""))
                .andExpect(status().isBadRequest());

        assertThat(transactionEntityRepository.count()).isZero();

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": 80.90,
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isCreated());

        assertThat(transactionEntityRepository.count()).isEqualTo(1);
    }

    // --- /transactions/ai sanity check: route exists, no external call reached ---

    @Test
    void aiEndpoint_missingFile_returns400_withoutReachingExternalServices() throws Exception {
        mockMvc.perform(multipart("/transactions/ai"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Arquivo obrigatório"));
    }

    private void createViaHttp(String description, String amount, Category category) throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "%s",
                                  "amount": %s,
                                  "category": "%s"
                                }""".formatted(description, amount, category)))
                .andExpect(status().isCreated());
    }
}
