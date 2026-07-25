package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Payload de cadastro de uma transação financeira via REST")
public record TransactionRequest(

        @Schema(description = "Descrição do gasto (obrigatória, não pode ser vazia)", example = "Combustível")
        @NotBlank(message = "A descrição da transação não pode estar vazia.")
        String description,

        @Schema(description = "Categoria da transação (obrigatória)")
        @NotNull(message = "A categoria da transação é obrigatória.")
        Category category,

        @Schema(
                description = "Valor da transação em reais, com até duas casas decimais. Deve ser maior que zero. "
                        + "O valor é normalizado para duas casas decimais (RoundingMode.HALF_UP) ao ser persistido.",
                example = "80.90"
        )
        @NotNull(message = "O valor da transação é obrigatório.")
        @DecimalMin(value = "0.00", inclusive = false, message = "O valor da transação deve ser maior que zero.")
        BigDecimal amount) {
    public PersistTransactionInput toInput() {
        return new PersistTransactionInput(description, amount, category);
    }
}
