package dio.budgeting.infrastructure.config;

import dio.budgeting.application.GetSpendingSummaryUseCase;
import dio.budgeting.application.GetTotalByCategoryUseCase;
import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /** ChatClient do assistente, com os casos de uso registrados como ferramentas (tool calling). */
    @Bean
    ChatClient budgetChatClient(ChatClient.Builder builder,
                                PersistTransactionUseCase persistTransactionUseCase,
                                ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
                                GetTotalByCategoryUseCase getTotalByCategoryUseCase,
                                GetSpendingSummaryUseCase getSpendingSummaryUseCase) {
        return builder
                .defaultTools(persistTransactionUseCase,
                        listTransactionsByCategoryUseCase,
                        getTotalByCategoryUseCase,
                        getSpendingSummaryUseCase)
                .build();
    }
}
