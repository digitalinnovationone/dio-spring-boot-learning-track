package dio.budgeting.application;

import dio.budgeting.application.output.CategoryTotalOutput;
import dio.budgeting.application.output.Money;
import dio.budgeting.application.output.SpendingSummaryOutput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
public class GetSpendingSummaryUseCase {
    private final TransactionRepository transactionRepository;

    public GetSpendingSummaryUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "get-spending-summary",
            description = "Resume os gastos de um período: total em reais, total por categoria e lista de transações")
    public SpendingSummaryOutput execute(
            @ToolParam(description = "Data inicial do período (inclusiva), formato yyyy-MM-dd") LocalDate start,
            @ToolParam(description = "Data final do período (inclusiva), formato yyyy-MM-dd") LocalDate end) {
        if (start == null || end == null) {
            throw new InvalidPeriodException("As datas inicial e final do período são obrigatórias");
        }
        if (start.isAfter(end)) {
            throw new InvalidPeriodException("A data inicial deve ser anterior ou igual à data final");
        }

        var transactions = transactionRepository.findAllByDateBetween(start, end);
        var totalCents = transactions.stream().mapToLong(Transaction::getAmount).sum();

        var byCategory = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getCategory))
                .entrySet().stream()
                .map(entry -> CategoryTotalOutput.from(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategoryTotalOutput::total).reversed())
                .toList();

        return new SpendingSummaryOutput(
                start,
                end,
                transactions.size(),
                Money.fromCents(totalCents),
                byCategory,
                transactions.stream().map(TransactionOutput::from).toList());
    }
}
