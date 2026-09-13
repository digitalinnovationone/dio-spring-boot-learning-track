package dio.budgeting.application;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetFinancialSummaryUseCaseTest {

    private TransactionRepository transactionRepository;
    private GetFinancialSummaryUseCase useCase;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        useCase = new GetFinancialSummaryUseCase(transactionRepository);
    }

    @Test
    @DisplayName("Deve retornar resumo zerado quando não houver transações")
    void shouldReturnEmptySummaryWhenNoTransactionsExist() {
        when(transactionRepository.findAll()).thenReturn(List.of());

        var summary = useCase.execute();

        assertNotNull(summary);
        assertEquals(0.0, summary.totalSpent());
        assertEquals(0, summary.totalTransactions());
        assertEquals(0.0, summary.averageSpent());
        assertEquals(0.0, summary.maxSingleSpent());
        assertTrue(summary.totalByCategory().isEmpty());
    }

    @Test
    @DisplayName("Deve calcular resumo financeiro corretamente para transações existentes")
    void shouldCalculateSummaryCorrectly() {
        var t1 = new Transaction("Supermercado", 15000, Category.GROCERIES); // 150.00
        var t2 = new Transaction("Remédio", 5000, Category.PHARMA); // 50.00
        var t3 = new Transaction("Feira", 5000, Category.GROCERIES); // 50.00

        when(transactionRepository.findAll()).thenReturn(List.of(t1, t2, t3));

        var summary = useCase.execute();

        assertNotNull(summary);
        assertEquals(250.0, summary.totalSpent());
        assertEquals(3, summary.totalTransactions());
        assertEquals(83.33, summary.averageSpent());
        assertEquals(150.0, summary.maxSingleSpent());
        assertEquals(200.0, summary.totalByCategory().get("GROCERIES"));
        assertEquals(50.0, summary.totalByCategory().get("PHARMA"));
    }
}
