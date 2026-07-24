package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload de cadastro de uma transação financeira via REST")
public record TransactionRequest(

        @Schema(description = "Descrição do gasto", example = "Combustível")
        String description,

        @Schema(description = "Categoria da transação")
        Category category,

        @Schema(
                description = "Valor do gasto. O tipo é `long` e o código atual não aplica nenhuma conversão de "
                        + "escala nem valida a unidade (reais ou centavos); a unidade real ainda não foi definida "
                        + "de forma consistente no projeto e será padronizada em uma tarefa futura, sem alterar o "
                        + "contrato atual deste campo.",
                example = "80"
        )
        long amount) {
    public PersistTransactionInput toInput() {
        return new PersistTransactionInput(description, amount, category);
    }
}
