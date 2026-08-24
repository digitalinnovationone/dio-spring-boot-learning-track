package dio.budgeting.application;

import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.security.AuthenticatedUser;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListTransactionsByCategoryUseCase {
    private final TransactionRepository transactionRepository;

    public ListTransactionsByCategoryUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<TransactionOutput> execute(Category category, UserId userId) {
        return transactionRepository.findAllByUserIdAndCategory(userId, category)
                .stream()
                .map(TransactionOutput::from)
                .toList();
    }

    @Tool(name = "list-transactions-by-category", description = "Lista transações financeiras por categoria")
    public List<TransactionOutput> execute(@ToolParam(description = "Categoria de uma transação") Category category) {
        return execute(category, getAuthenticatedUserId());
    }

    private UserId getAuthenticatedUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getId();
        }
        throw new IllegalStateException("User must be authenticated to list transactions");
    }
}
