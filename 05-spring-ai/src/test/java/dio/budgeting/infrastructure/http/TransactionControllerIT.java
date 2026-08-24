package dio.budgeting.infrastructure.http;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class TransactionControllerIT {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(WebApplicationContext webApplicationContext) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private String registerAndGetToken(String username, String password) throws Exception {
        String registerJson = String.format("""
                {
                    "username": "%s",
                    "password": "%s"
                }
                """, username, password);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson));

        String loginJson = String.format("""
                {
                    "username": "%s",
                    "password": "%s"
                }
                """, username, password);

        String loginResponse = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(loginResponse, "$.token");
    }

    @Test
    void shouldIsolateTransactionsBetweenDifferentUsers() throws Exception {
        String tokenUser1 = registerAndGetToken("user_one", "password123");
        String tokenUser2 = registerAndGetToken("user_two", "password123");

        // User 1 cria transação no Mercado A
        String txUser1 = """
                {
                    "description": "Mercado A - User 1",
                    "category": "GROCERIES",
                    "amount": 10000
                }
                """;
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txUser1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Mercado A - User 1"));

        // User 2 cria transação no Mercado B
        String txUser2 = """
                {
                    "description": "Mercado B - User 2",
                    "category": "GROCERIES",
                    "amount": 5000
                }
                """;
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + tokenUser2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txUser2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Mercado B - User 2"));

        // User 1 consulta transações de GROCERIES -> deve ver apenas a sua (Mercado A)
        mockMvc.perform(get("/transactions/GROCERIES")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Mercado A - User 1"));

        // User 2 consulta transações de GROCERIES -> deve ver apenas a sua (Mercado B)
        mockMvc.perform(get("/transactions/GROCERIES")
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Mercado B - User 2"));
    }
}
