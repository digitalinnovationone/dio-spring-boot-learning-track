package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.TransactionOutput;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Transação financeira persistida, retornada pelo cadastro e pela consulta por categoria")
public record TransactionResponse(

        @Schema(description = "Identificador único da transação (UUID)", example = "3f1c9e2a-2b8b-4e0f-8b7a-1f2e3d4c5b6a")
        String id,

        @Schema(description = "Categoria da transação")
        String category,

        @Schema(description = "Descrição do gasto", example = "Combustível")
        String description,

        @Schema(
                description = "Valor da transação, formatado com duas casas decimais. Não há conversão de escala "
                        + "aplicada sobre o valor de entrada (ver observação em TransactionRequest.amount).",
                example = "80.0"
        )
        double amount) {
    public static TransactionResponse from(TransactionOutput output) {
        return new TransactionResponse(output.id(), output.category(), output.description(), output.value());
    }
}
