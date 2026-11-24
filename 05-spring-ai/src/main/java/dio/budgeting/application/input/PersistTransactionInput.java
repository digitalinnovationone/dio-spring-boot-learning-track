package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;

public record PersistTransactionInput(
        @ToolParam(description = "Descrição curta do gasto, por exemplo 'Compras no mercado'") String description,
        @ToolParam(description = "Valor do gasto em centavos. Exemplo: 80 reais = 8000") long amount,
        @ToolParam(description = "Categoria de uma transação") Category category,
        @ToolParam(required = false,
                description = "Data do gasto no formato yyyy-MM-dd. Omita para usar a data de hoje") LocalDate date) {
}
