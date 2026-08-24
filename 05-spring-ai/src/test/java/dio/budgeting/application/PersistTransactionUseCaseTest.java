package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.*;
import dio.budgeting.infrastructure.security.AuthenticatedUser;
import dio.budgeting.infrastructure.storage.AudioRequestContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistTransactionUseCaseTest {

    @Mock
    private TransactionRepository transactionRepository;

    private PersistTransactionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PersistTransactionUseCase(transactionRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        AudioRequestContextHolder.clear();
    }

    @Test
    void shouldPersistTransactionWithAuthenticatedUserAndAudioRecordIdFromContext() {
        UserId userId = new UserId();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(userId, "voice_user", "password", UserRole.ROLE_USER);
        var auth = new UsernamePasswordAuthenticationToken(authenticatedUser, null, authenticatedUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        AudioRecordId audioRecordId = new AudioRecordId();
        AudioRequestContextHolder.set(audioRecordId);

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PersistTransactionInput input = new PersistTransactionInput("Farmacia Drogasil", 8500L, Category.PHARMA);
        var output = useCase.execute(input);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());

        Transaction saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getAudioRecordId()).isEqualTo(audioRecordId);
        assertThat(saved.getDescription()).isEqualTo("Farmacia Drogasil");
        assertThat(saved.getAmount()).isEqualTo(8500L);
        assertThat(saved.getCategory()).isEqualTo(Category.PHARMA);
        assertThat(saved.getCreatedAt()).isNotNull();

        assertThat(output.audioRecordId()).isEqualTo(audioRecordId.uuid().toString());
        assertThat(output.value()).isEqualTo(8500.0);
    }
}
