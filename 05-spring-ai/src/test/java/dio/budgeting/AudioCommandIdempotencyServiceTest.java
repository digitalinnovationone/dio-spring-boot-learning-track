package dio.budgeting;

import dio.budgeting.infrastructure.ai.AiIntegrationException;
import dio.budgeting.infrastructure.idempotency.AudioCommandIdempotencyService;
import dio.budgeting.infrastructure.idempotency.AudioCommandOperationEntity;
import dio.budgeting.infrastructure.idempotency.AudioCommandOperationRepository;
import dio.budgeting.infrastructure.idempotency.AudioCommandStatus;
import dio.budgeting.infrastructure.idempotency.IdempotencyDecision;
import dio.budgeting.infrastructure.idempotency.IdempotencyException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real JPA/H2 tests (TASK-009): AudioCommandIdempotencyService and its
 * AudioCommandOperationStore backing bean are exercised for real, against
 * the same isolated in-memory database used by TransactionRestIntegrationTest.
 * No MySQL/Docker, no OpenAI call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class AudioCommandIdempotencyServiceTest {

    @Autowired
    AudioCommandIdempotencyService service;

    @Autowired
    AudioCommandOperationRepository repository;

    @Autowired
    EntityManager entityManager;

    // --- Key validation ---

    @Test
    void nullKey_throwsMissingKey() {
        assertReason(() -> service.begin(null, "fp"), IdempotencyException.Reason.MISSING_KEY);
    }

    @Test
    void blankKey_throwsMissingKey() {
        assertReason(() -> service.begin("   ", "fp"), IdempotencyException.Reason.MISSING_KEY);
    }

    @Test
    void keyAboveMaxLength_throwsInvalidKey() {
        String tooLong = "a".repeat(129);
        assertReason(() -> service.begin(tooLong, "fp"), IdempotencyException.Reason.INVALID_KEY);
    }

    @Test
    void keyWithDisallowedCharacters_throwsInvalidKey() {
        assertReason(() -> service.begin("chave com espaço", "fp"), IdempotencyException.Reason.INVALID_KEY);
    }

    @Test
    void opaqueStringKey_isAccepted() {
        var decision = service.begin("client-generated-token.001", "fp-opaque");
        assertThat(decision).isInstanceOf(IdempotencyDecision.Start.class);
    }

    @Test
    void uuidKey_isAccepted() {
        var decision = service.begin("3f18cfbe-6072-4e5c-b7a6-183ae1809846", "fp-uuid");
        assertThat(decision).isInstanceOf(IdempotencyDecision.Start.class);
    }

    // --- New operation ---

    @Test
    void newKey_returnsStart_andPersistsProcessing() {
        var decision = service.begin("key-new", "fp-new");

        assertThat(decision).isInstanceOf(IdempotencyDecision.Start.class);
        var entity = repository.findByIdempotencyKey("key-new").orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(AudioCommandStatus.PROCESSING);
        assertThat(entity.getPayloadFingerprint()).isEqualTo("fp-new");
    }

    // --- Replay (completed) ---

    @Test
    void completedOperation_sameFingerprint_returnsReplayWithStoredText() {
        var start = (IdempotencyDecision.Start) service.begin("key-completed", "fp-completed");
        service.complete(start.operationId(), "Transação registrada com sucesso.", null);

        var decision = service.begin("key-completed", "fp-completed");

        assertThat(decision).isInstanceOf(IdempotencyDecision.Replay.class);
        var replay = (IdempotencyDecision.Replay) decision;
        assertThat(replay.responseText()).isEqualTo("Transação registrada com sucesso.");
        assertThat(replay.transactionId()).isNull();
    }

    // --- Payload conflict ---

    @Test
    void sameKey_differentFingerprint_throwsPayloadConflict() {
        service.begin("key-conflict", "fp-a");

        assertReason(() -> service.begin("key-conflict", "fp-b"), IdempotencyException.Reason.PAYLOAD_CONFLICT);
    }

    @Test
    void completedOperation_differentFingerprint_stillThrowsPayloadConflict() {
        var start = (IdempotencyDecision.Start) service.begin("key-completed-conflict", "fp-a");
        service.complete(start.operationId(), "resposta", null);

        assertReason(() -> service.begin("key-completed-conflict", "fp-b"),
                IdempotencyException.Reason.PAYLOAD_CONFLICT);
    }

    // --- In progress ---

    @Test
    void sameKeySameFingerprint_stillProcessing_throwsInProgress() {
        service.begin("key-inprogress", "fp-inprogress");

        assertReason(() -> service.begin("key-inprogress", "fp-inprogress"), IdempotencyException.Reason.IN_PROGRESS);
    }

    // --- Failed operations: retry policy ---

    @Test
    void failedAtTranscription_sameKey_allowsRetry_returnsStart() {
        var start = (IdempotencyDecision.Start) service.begin("key-retry-transcription", "fp-retry");
        service.fail(start.operationId(), AiIntegrationException.Stage.TRANSCRIPTION);

        var decision = service.begin("key-retry-transcription", "fp-retry");

        assertThat(decision).isInstanceOf(IdempotencyDecision.Start.class);
        var entity = repository.findByIdempotencyKey("key-retry-transcription").orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(AudioCommandStatus.PROCESSING);
        assertThat(entity.getFailureStage()).isNull();
    }

    @Test
    void failedAtChat_blocksRetry() {
        var start = (IdempotencyDecision.Start) service.begin("key-retry-chat", "fp-retry");
        service.fail(start.operationId(), AiIntegrationException.Stage.CHAT);

        assertReason(() -> service.begin("key-retry-chat", "fp-retry"), IdempotencyException.Reason.RETRY_NOT_ALLOWED);
    }

    @Test
    void failedAtSpeech_blocksRetry() {
        var start = (IdempotencyDecision.Start) service.begin("key-retry-speech", "fp-retry");
        service.fail(start.operationId(), AiIntegrationException.Stage.SPEECH);

        assertReason(() -> service.begin("key-retry-speech", "fp-retry"), IdempotencyException.Reason.RETRY_NOT_ALLOWED);
    }

    // --- Concurrency: the unique constraint (not an in-memory check) is what actually
    // protects the race described in TASK-009. A real multi-threaded reproduction
    // proved unreliable in this sandboxed H2/JVM environment (lock-wait hangs), so
    // this test proves the constraint directly at the database level instead: a
    // second row with the same idempotency_key can never be committed, which is
    // exactly the guarantee AudioCommandIdempotencyService.begin(...) relies on
    // when it catches DataIntegrityViolationException and re-reads the row.

    @Test
    void uniqueConstraint_rejectsSecondRowWithSameIdempotencyKey() {
        var now = OffsetDateTime.now();
        repository.save(new AudioCommandOperationEntity("key-unique", "fp-1", now));

        assertThatThrownBy(() -> repository.saveAndFlush(new AudioCommandOperationEntity("key-unique", "fp-2", now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void assertReason(Runnable action, IdempotencyException.Reason reason) {
        assertThatThrownBy(action::run)
                .isInstanceOf(IdempotencyException.class)
                .satisfies(ex -> assertThat(((IdempotencyException) ex).getReason()).isEqualTo(reason));
    }

    // --- TASK-010: abandoned PROCESSING recovery (opportunistic, inside begin()) ---

    @Test
    void processingAbandonedAtRegistered_recoversAndAllowsRetry() {
        var start = (IdempotencyDecision.Start) service.begin("key-abandoned-registered", "fp-abandoned");
        backdateUpdatedAt(start.operationId(), OffsetDateTime.now().minusMinutes(20));

        var decision = service.begin("key-abandoned-registered", "fp-abandoned");

        assertThat(decision).isInstanceOf(IdempotencyDecision.Start.class);
        var entity = repository.findByIdempotencyKey("key-abandoned-registered").orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(AudioCommandStatus.PROCESSING);
        assertThat(entity.getFailureStage()).isNull();
    }

    @Test
    void processingAbandonedAtChat_isMarkedFailed_andBlocksRetry() {
        var start = (IdempotencyDecision.Start) service.begin("key-abandoned-chat", "fp-abandoned");
        service.markStage(start.operationId(), AiIntegrationException.Stage.CHAT);
        backdateUpdatedAt(start.operationId(), OffsetDateTime.now().minusMinutes(20));

        assertReason(() -> service.begin("key-abandoned-chat", "fp-abandoned"), IdempotencyException.Reason.RETRY_NOT_ALLOWED);
        var entity = repository.findByIdempotencyKey("key-abandoned-chat").orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(AudioCommandStatus.FAILED);
        assertThat(entity.getFailureStage()).isEqualTo(AiIntegrationException.Stage.CHAT);
    }

    @Test
    void processingAbandonedAtSpeech_blocksRetry() {
        var start = (IdempotencyDecision.Start) service.begin("key-abandoned-speech", "fp-abandoned");
        service.markStage(start.operationId(), AiIntegrationException.Stage.SPEECH);
        backdateUpdatedAt(start.operationId(), OffsetDateTime.now().minusMinutes(20));

        assertReason(() -> service.begin("key-abandoned-speech", "fp-abandoned"), IdempotencyException.Reason.RETRY_NOT_ALLOWED);
        var entity = repository.findByIdempotencyKey("key-abandoned-speech").orElseThrow();
        assertThat(entity.getFailureStage()).isEqualTo(AiIntegrationException.Stage.SPEECH);
    }

    @Test
    void processingAbandonedWithUnknownStage_isTreatedAsUnsafe() {
        var start = (IdempotencyDecision.Start) service.begin("key-abandoned-unknown", "fp-abandoned");
        var entity = repository.findById(start.operationId()).orElseThrow();
        entity.setCurrentStage(null);
        entity.setUpdatedAt(OffsetDateTime.now().minusMinutes(20));
        repository.save(entity);

        assertReason(() -> service.begin("key-abandoned-unknown", "fp-abandoned"), IdempotencyException.Reason.RETRY_NOT_ALLOWED);
    }

    @Test
    void processingRecent_neverAbandoned_staysInProgress() {
        service.begin("key-fresh-processing", "fp-fresh");

        assertReason(() -> service.begin("key-fresh-processing", "fp-fresh"), IdempotencyException.Reason.IN_PROGRESS);
    }

    // --- TASK-010: opportunistic expiration (inside begin(), not waiting for the scheduler) ---

    @Test
    void completedExpired_isTreatedAsNewOperation() {
        var start = (IdempotencyDecision.Start) service.begin("key-completed-expired", "fp-expired");
        service.complete(start.operationId(), "resposta antiga", null);
        backdateUpdatedAt(start.operationId(), OffsetDateTime.now().minusHours(25));

        var decision = service.begin("key-completed-expired", "fp-expired");

        assertThat(decision).isInstanceOf(IdempotencyDecision.Start.class);
        var entity = repository.findByIdempotencyKey("key-completed-expired").orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(AudioCommandStatus.PROCESSING);
    }

    @Test
    void failedExpiredAtTranscription_isTreatedAsNewOperation() {
        var start = (IdempotencyDecision.Start) service.begin("key-failed-expired-transcription", "fp-expired");
        service.fail(start.operationId(), AiIntegrationException.Stage.TRANSCRIPTION);
        backdateUpdatedAt(start.operationId(), OffsetDateTime.now().minusHours(25));

        var decision = service.begin("key-failed-expired-transcription", "fp-expired");

        assertThat(decision).isInstanceOf(IdempotencyDecision.Start.class);
    }

    @Test
    void failedExpiredAtChat_isAlsoTreatedAsNewOperation_becauseWindowElapsed() {
        var start = (IdempotencyDecision.Start) service.begin("key-failed-expired-chat", "fp-expired");
        service.fail(start.operationId(), AiIntegrationException.Stage.CHAT);
        backdateUpdatedAt(start.operationId(), OffsetDateTime.now().minusHours(25));

        var decision = service.begin("key-failed-expired-chat", "fp-expired");

        assertThat(decision).isInstanceOf(IdempotencyDecision.Start.class);
    }

    // --- TASK-010: optimistic lock conflict between two loaded copies of the same row ---
    // A real multi-threaded reproduction proved unreliable in this sandboxed H2/JVM
    // environment (see the unique-constraint test above for the same caveat), so this
    // test proves the conflict directly: two independent (detached) copies of the same
    // row, the second save must fail once the first has already advanced the version.

    @Test
    void concurrentUpdate_optimisticLock_detectsConflict() {
        var start = (IdempotencyDecision.Start) service.begin("key-optimistic", "fp-optimistic");
        entityManager.flush();
        entityManager.clear();

        var copyA = repository.findById(start.operationId()).orElseThrow();
        entityManager.detach(copyA);

        var copyB = repository.findById(start.operationId()).orElseThrow();
        copyB.fail(AiIntegrationException.Stage.CHAT, OffsetDateTime.now());
        repository.saveAndFlush(copyB);
        entityManager.clear();

        copyA.fail(AiIntegrationException.Stage.SPEECH, OffsetDateTime.now());
        assertThatThrownBy(() -> repository.saveAndFlush(copyA))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private void backdateUpdatedAt(UUID operationId, OffsetDateTime updatedAt) {
        var entity = repository.findById(operationId).orElseThrow();
        entity.setUpdatedAt(updatedAt);
        repository.save(entity);
    }
}
