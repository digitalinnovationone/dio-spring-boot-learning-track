package dio.budgeting.infrastructure.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthControllerIT {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(WebApplicationContext webApplicationContext) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldRegisterUserAndLoginSuccessfully() throws Exception {
        String registerJson = """
                {
                    "username": "tester_user",
                    "password": "strongPassword123"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("tester_user"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        String loginJson = """
                {
                    "username": "tester_user",
                    "password": "strongPassword123"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.expiresInMs").isNotEmpty());
    }

    @Test
    void shouldFailLoginWhenCredentialsAreInvalid() throws Exception {
        String invalidLoginJson = """
                {
                    "username": "non_existing_user",
                    "password": "wrongPassword"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidLoginJson))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDenyAccessToProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/transactions/GROCERIES"))
                .andExpect(status().isForbidden());
    }
}
