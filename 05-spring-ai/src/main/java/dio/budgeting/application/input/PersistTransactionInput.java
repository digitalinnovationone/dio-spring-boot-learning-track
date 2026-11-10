package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.ai.tool.annotation.ToolParam;

public record PersistTransactionInput(
        @ToolParam(description = "Descrição do gasto")
        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 255, message = "Descrição deve ter no máximo 255 caracteres")
        String description,
        @ToolParam(description = "Valor do gasto (em centavos)")
        @Positive(message = "Valor deve ser maior que zero")
        long amount,
        @ToolParam(description = "Categoria de uma transação")
        @NotNull(message = "Categoria é obrigatória")
        Category category
) {
}
