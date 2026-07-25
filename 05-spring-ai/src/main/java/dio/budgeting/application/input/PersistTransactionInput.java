package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;

public record PersistTransactionInput(@ToolParam(description = "Descrição do gasto (obrigatória, não pode ser vazia)") String description,
                                      @ToolParam(description = "Valor da transação em reais, por exemplo 80.90. Deve ser maior que zero") BigDecimal amount,
                                      @ToolParam(description = "Categoria de uma transação (obrigatória)") Category category) {
}
