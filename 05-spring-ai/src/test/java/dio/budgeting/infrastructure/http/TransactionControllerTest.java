package dio.budgeting.infrastructure.http;

import dio.budgeting.application.GetCategoryTotalUseCase;
import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.SummarizeExpensesUseCase;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionControllerTest {
    private MockMvc mockMvc;
    private InMemoryTransactionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new InMemoryTransactionRepository();

        var controller = new TransactionController(
                new PersistTransactionUseCase(repository),
                new ListTransactionsByCategoryUseCase(repository),
                new SummarizeExpensesUseCase(repository),
                new GetCategoryTotalUseCase(repository),
                mock(TranscriptionModel.class),
                new ByteArrayResource("Você é um assistente financeiro.".getBytes()),
                mock(ChatClient.Builder.class, Mockito.RETURNS_SELF),
                mock(TextToSpeechModel.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void createsTransactionAndReturnsValueInReais() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Mercado\",\"category\":\"GROCERIES\",\"amount\":1500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Mercado"))
                .andExpect(jsonPath("$.category").value("GROCERIES"))
                .andExpect(jsonPath("$.amount").value(15.0));
    }

    @Test
    void rejectsInvalidAmountWith400() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Mercado\",\"category\":\"GROCERIES\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void rejectsBlankDescriptionWith400() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"\",\"category\":\"GROCERIES\",\"amount\":1500}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsConsolidatedSummary() throws Exception {
        repository.save(new Transaction("Mercado", 1000, Category.GROCERIES));
        repository.save(new Transaction("Farmácia", 5000, Category.PHARMA));

        mockMvc.perform(get("/transactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(60.0))
                .andExpect(jsonPath("$.totalsByCategory.GROCERIES").value(10.0))
                .andExpect(jsonPath("$.totalsByCategory.PHARMA").value(50.0));
    }

    @Test
    void returnsCategoryTotal() throws Exception {
        repository.save(new Transaction("Mercado", 1000, Category.GROCERIES));
        repository.save(new Transaction("Feira", 2550, Category.GROCERIES));

        mockMvc.perform(get("/transactions/total/GROCERIES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("GROCERIES"))
                .andExpect(jsonPath("$.total").value(35.5));
    }

    @Test
    void listsTransactionsByCategory() throws Exception {
        repository.save(new Transaction("Mercado", 1000, Category.GROCERIES));

        mockMvc.perform(get("/transactions/GROCERIES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Mercado"))
                .andExpect(jsonPath("$[0].amount").value(10.0));
    }

    @Test
    void rejectsInvalidCategoryPathWith400() throws Exception {
        mockMvc.perform(get("/transactions/total/INVALID"))
                .andExpect(status().isBadRequest());
    }
}
