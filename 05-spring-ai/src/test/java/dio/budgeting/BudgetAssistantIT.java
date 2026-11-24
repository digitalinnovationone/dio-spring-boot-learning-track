package dio.budgeting;

import dio.budgeting.application.BudgetAssistantUseCase;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Testa o tool calling de ponta a ponta com o modelo real (usa H2 em memória nos testes). */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class BudgetAssistantIT {
    @Autowired
    BudgetAssistantUseCase budgetAssistantUseCase;

    @Autowired
    TransactionRepository transactionRepository;

    @Test
    void should_persistTransactionInCents_when_userDescribesAnExpense() {
        var output = budgetAssistantUseCase.answerText("Gastei 80 reais no mercado");

        assertThat(transactionRepository.findAllByCategory(Category.GROCERIES))
                .anySatisfy(t -> assertThat(t.getAmount()).isEqualTo(8000));
        assertThat(output.answer()).isNotBlank();
        System.out.println(output.answer());
    }
}
