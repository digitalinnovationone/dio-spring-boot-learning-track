package dio.budgeting.domain;

import java.util.List;

public interface TransactionRepository {
    Transaction save(Transaction transaction);

    List<Transaction> findAllByUserIdAndCategory(UserId userId, Category category);

    List<Transaction> findAllByUserId(UserId userId);
}
