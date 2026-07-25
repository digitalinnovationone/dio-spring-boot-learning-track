package dio.budgeting.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Budgeting API",
                description = "API de controle financeiro com Spring Boot e Spring AI",
                version = "0.0.1-SNAPSHOT"
        )
)
public class OpenApiConfig {
}
