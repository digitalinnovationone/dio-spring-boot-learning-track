package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.AudioRecordId;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.security.AuthenticatedUser;
import dio.budgeting.infrastructure.storage.AudioRequestContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class PersistTransactionUseCase {
    private final TransactionRepository transactionRepository;

    public PersistTransactionUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public TransactionOutput execute(PersistTransactionInput input, UserId userId, AudioRecordId audioRecordId) {
        var transaction = transactionRepository.save(
                new Transaction(userId, audioRecordId, input.description(), input.amount(), input.category()));

        return TransactionOutput.from(transaction);
    }

    public TransactionOutput execute(PersistTransactionInput input, UserId userId) {
        return execute(input, userId, null);
    }

    @Tool(name = "persist-transaction", description = "Persiste uma nova transação financeira")
    public TransactionOutput execute(PersistTransactionInput input) {
        UserId userId = getAuthenticatedUserId();
        AudioRecordId audioRecordId = AudioRequestContextHolder.get();
        return execute(input, userId, audioRecordId);
    }

    private UserId getAuthenticatedUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getId();
        }
        throw new IllegalStateException("User must be authenticated to persist a transaction");
    }
}
