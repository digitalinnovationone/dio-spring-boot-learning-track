package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.InvalidTransactionException;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistTransactionUseCaseTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final PersistTransactionUseCase useCase = new PersistTransactionUseCase(repository, clock);

    @Test
    void should_returnValueInReais_when_amountIsInCents() {
        var output = useCase.execute(new PersistTransactionInput("Mercado", 8050, Category.GROCERIES, null));

        assertThat(output.value()).isEqualByComparingTo(new BigDecimal("80.50"));
        assertThat(repository.all()).singleElement()
                .satisfies(t -> assertThat(t.getAmount()).isEqualTo(8050));
    }

    @Test
    void should_useToday_when_dateIsOmitted() {
        var output = useCase.execute(new PersistTransactionInput("Farmácia", 2000, Category.PHARMA, null));

        assertThat(output.date()).isEqualTo(TODAY);
    }

    @Test
    void should_keepInformedDate_when_dateIsPresent() {
        var yesterday = TODAY.minusDays(1);

        var output = useCase.execute(new PersistTransactionInput("Posto", 15000, Category.AUTO, yesterday));

        assertThat(output.date()).isEqualTo(yesterday);
    }

    @Test
    void should_notPersist_when_transactionIsInvalid() {
        assertThatThrownBy(() -> useCase.execute(new PersistTransactionInput("Mercado", 0, Category.GROCERIES, null)))
                .isInstanceOf(InvalidTransactionException.class);
        assertThat(repository.all()).isEmpty();
    }
}
