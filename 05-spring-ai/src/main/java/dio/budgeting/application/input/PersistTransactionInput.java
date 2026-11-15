package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.ai.tool.annotation.ToolParam;

public record PersistTransactionInput(
        @NotBlank(message = "A descrição é obrigatória")
        @ToolParam(description = "Descrição do gasto") String description,

        @Positive(message = "O valor deve ser maior que zero (em centavos)")
        @ToolParam(description = "Valor do gasto (em centavos)") long amount,

        @NotNull(message = "A categoria é obrigatória")
        @ToolParam(description = "Categoria de uma transação") Category category) {
}
