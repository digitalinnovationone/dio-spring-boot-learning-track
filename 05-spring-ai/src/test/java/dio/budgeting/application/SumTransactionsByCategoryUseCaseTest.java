package dio.budgeting.application;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SumTransactionsByCategoryUseCaseTest {
    @Mock
    TransactionRepository transactionRepository;

    @Test
    void should_sumAllAmounts_when_categoryHasMultipleTransactions() {
        var first = new Transaction("Remédio", 1500, Category.PHARMA);
        var second = new Transaction("Vitamina", 2500, Category.PHARMA);
        when(transactionRepository.findAllByCategory(Category.PHARMA)).thenReturn(List.of(first, second));

        var useCase = new SumTransactionsByCategoryUseCase(transactionRepository);
        var result = useCase.execute(Category.PHARMA);

        assertThat(result.category()).isEqualTo(Category.PHARMA);
        assertThat(result.total()).isEqualTo(4000.00);
        assertThat(result.transactionCount()).isEqualTo(2);
    }

    @Test
    void should_returnZeroTotal_when_categoryHasNoTransactions() {
        when(transactionRepository.findAllByCategory(Category.AUTO)).thenReturn(List.of());

        var useCase = new SumTransactionsByCategoryUseCase(transactionRepository);
        var result = useCase.execute(Category.AUTO);

        assertThat(result.total()).isEqualTo(0.00);
        assertThat(result.transactionCount()).isEqualTo(0);
    }
}
