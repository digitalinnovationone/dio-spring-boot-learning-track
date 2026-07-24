package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;

public record PersistTransactionInput(@ToolParam(description = "Descrição do gasto") String description,
                                      @ToolParam(description = "Valor da transação em reais, por exemplo 80.90") BigDecimal amount,
                                      @ToolParam(description = "Categoria de uma transação") Category category) {
}
