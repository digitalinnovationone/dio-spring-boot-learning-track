package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Payload de cadastro de uma transação financeira via REST")
public record TransactionRequest(

        @Schema(description = "Descrição do gasto", example = "Combustível")
        String description,

        @Schema(description = "Categoria da transação")
        Category category,

        @Schema(
                description = "Valor da transação em reais, com até duas casas decimais. O valor é normalizado "
                        + "para duas casas decimais (RoundingMode.HALF_UP) ao ser persistido.",
                example = "80.90"
        )
        BigDecimal amount) {
    public PersistTransactionInput toInput() {
        return new PersistTransactionInput(description, amount, category);
    }
}
