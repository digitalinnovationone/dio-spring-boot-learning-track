package dio.budgeting.infrastructure.http;

import dio.budgeting.application.BudgetAssistantUseCase;
import dio.budgeting.application.GetSpendingSummaryUseCase;
import dio.budgeting.application.GetTotalByCategoryUseCase;
import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infrastructure.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TransactionController.class, AssistantController.class})
@Import({PersistTransactionUseCase.class, ListTransactionsByCategoryUseCase.class,
        GetTotalByCategoryUseCase.class, GetSpendingSummaryUseCase.class, ClockConfig.class})
class TransactionControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TransactionRepository transactionRepository;

    @MockitoBean
    BudgetAssistantUseCase budgetAssistantUseCase;

    @Test
    void should_createTransaction_and_returnAmountInReais() throws Exception {
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Mercado", "category": "GROCERIES", "amount": 8000, "date": "2026-11-20"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(80.0))
                .andExpect(jsonPath("$.date").value("2026-11-20"));
    }

    @Test
    void should_return400_when_amountIsNotPositive() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Mercado", "category": "GROCERIES", "amount": -10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O valor da transação deve ser maior que zero"));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void should_return400_when_categoryIsUnknown() throws Exception {
        mockMvc.perform(get("/transactions/FOOD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parâmetro inválido"));
    }

    @Test
    void should_return400_when_periodIsInverted() throws Exception {
        mockMvc.perform(get("/transactions/summary").param("start", "2026-11-30").param("end", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_when_audioFileIsEmpty() throws Exception {
        var empty = new MockMultipartFile("file", "audio.m4a", "audio/mp4", new byte[0]);

        mockMvc.perform(multipart("/transactions/ai/text").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O arquivo de áudio está vazio"));
        verifyNoInteractions(budgetAssistantUseCase);
    }
}
