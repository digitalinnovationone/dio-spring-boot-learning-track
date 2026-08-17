package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionType;
import org.springframework.ai.tool.annotation.ToolParam;

public record PersistTransactionInput(@ToolParam(description = "Descrição do gasto") String description,
                                      @ToolParam(description = "Valor do gasto (em centavos)") long amount,
                                      @ToolParam(description = "Categoria de uma transação") Category category,
                                      @ToolParam(description = "Tipo da transação: EXPENSE para despesas/gastos ou INCOME para receitas/ganhos") TransactionType type) {
}
