package dio.budgeting;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sobe o contexto completo com H2 em memória e uma chave fictícia: nenhuma chamada à OpenAI é feita.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
class BudgetingApplicationTests {

    @Test
    void contextLoads() {
    }

}
