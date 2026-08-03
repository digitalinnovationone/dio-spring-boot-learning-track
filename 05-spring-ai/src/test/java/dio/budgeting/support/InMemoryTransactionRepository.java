package dio.budgeting.support;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementação em memória de {@link TransactionRepository} para testes de
 * unidade dos casos de uso, sem banco de dados nem dependências externas.
 */
public class InMemoryTransactionRepository implements TransactionRepository {
    private final List<Transaction> transactions = new ArrayList<>();

    @Override
    public Transaction save(Transaction transaction) {
        transactions.add(transaction);
        return transaction;
    }

    @Override
    public List<Transaction> findAllByCategory(Category category) {
        return transactions.stream()
                .filter(transaction -> transaction.getCategory() == category)
                .toList();
    }

    @Override
    public List<Transaction> findAll() {
        return List.copyOf(transactions);
    }
}
