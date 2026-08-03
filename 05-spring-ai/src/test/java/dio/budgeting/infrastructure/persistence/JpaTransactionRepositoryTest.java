package dio.budgeting.infrastructure.persistence;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a persistência real (mapeamento entidade↔domínio e queries) contra o
 * banco H2 configurado no perfil de teste. Cada teste roda em transação com
 * rollback automático, mantendo o estado isolado.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JpaTransactionRepositoryTest {

    @Autowired
    private TransactionRepository repository;

    @Test
    void savesAndMapsTransactionBackToDomain() {
        var saved = repository.save(new Transaction("Mercado", 1500, Category.GROCERIES));

        var found = repository.findAllByCategory(Category.GROCERIES);

        assertThat(found).hasSize(1);
        var transaction = found.get(0);
        assertThat(transaction.getId().uuid()).isEqualTo(saved.getId().uuid());
        assertThat(transaction.getDescription()).isEqualTo("Mercado");
        assertThat(transaction.getAmount()).isEqualTo(1500);
        assertThat(transaction.getCategory()).isEqualTo(Category.GROCERIES);
    }

    @Test
    void filtersByCategory() {
        repository.save(new Transaction("Mercado", 1000, Category.GROCERIES));
        repository.save(new Transaction("Farmácia", 2000, Category.PHARMA));

        assertThat(repository.findAllByCategory(Category.PHARMA)).hasSize(1);
        assertThat(repository.findAllByCategory(Category.AUTO)).isEmpty();
    }

    @Test
    void findAllReturnsEveryTransaction() {
        repository.save(new Transaction("Mercado", 1000, Category.GROCERIES));
        repository.save(new Transaction("Farmácia", 2000, Category.PHARMA));
        repository.save(new Transaction("Posto", 3000, Category.AUTO));

        assertThat(repository.findAll()).hasSize(3);
    }
}
