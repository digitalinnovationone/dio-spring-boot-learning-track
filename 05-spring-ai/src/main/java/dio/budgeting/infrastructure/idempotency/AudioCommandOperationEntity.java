package dio.budgeting.infrastructure.idempotency;

import dio.budgeting.infrastructure.ai.AiIntegrationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tracks one POST /transactions/ai invocation identified by an
 * {@code Idempotency-Key}. Never stores the audio itself (input or
 * generated) - only {@code responseText} (the safe final ChatClient reply),
 * so a replay of a COMPLETED operation can regenerate audio via TTS alone,
 * without repeating transcription/chat/Tool Calling.
 *
 * <p>Status transitions and their timestamps are only ever performed through
 * the domain methods below ({@link #markStage}, {@link #complete}, {@link
 * #fail}, {@link #restartForRetry}) - never through the Lombok setters
 * directly from production code (kept only for test fixtures that need to
 * backdate {@code updatedAt} to simulate an old row) - so status, stage and
 * {@code updatedAt} can never drift out of sync with each other.
 */
@Entity
@Table(name = "audio_command_operation",
        uniqueConstraints = @UniqueConstraint(name = "uk_audio_command_operation_key", columnNames = "idempotency_key"),
        indexes = @Index(name = "ix_audio_command_operation_status_updated_at", columnList = "status, updated_at"))
@Getter
@Setter
@NoArgsConstructor
public class AudioCommandOperationEntity {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 128, unique = true)
    private String idempotencyKey;

    @Column(name = "payload_fingerprint", nullable = false, length = 64)
    private String payloadFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AudioCommandStatus status;

    /**
     * Stage where a FAILED operation stopped. Null unless status is FAILED.
     * Drives the retry policy in AudioCommandIdempotencyService: TRANSCRIPTION
     * failures may retry (no side effect could have happened yet); CHAT/SPEECH
     * failures block reuse (Tool Calling may already have persisted).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage", length = 20)
    private AiIntegrationException.Stage failureStage;

    /**
     * Stage the CURRENT (or last) processing attempt has reached - see {@link
     * AudioCommandStage}. Distinct from {@code failureStage}: this is updated
     * throughout a live PROCESSING run (before each external call), while
     * failureStage is only ever set once an operation has actually failed.
     * Nullable for schema evolution (a pre-existing row without this column is
     * treated as unknown/unsafe by {@code IdempotencyExpirationPolicy}, never
     * as REGISTERED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", length = 20)
    private AudioCommandStage currentStage;

    /**
     * Always null in this task: the transaction ID created by Tool Calling
     * cannot be captured safely without either duplicating the @Tool
     * annotation on a wrapper or parsing the AI's text response (both
     * rejected). Kept as a nullable column for a future task that solves this
     * properly.
     */
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "response_text", length = 2000)
    private String responseText;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    public AudioCommandOperationEntity(String idempotencyKey, String payloadFingerprint, OffsetDateTime now) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.payloadFingerprint = payloadFingerprint;
        this.status = AudioCommandStatus.PROCESSING;
        this.currentStage = AudioCommandStage.REGISTERED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markStage(AudioCommandStage stage, OffsetDateTime now) {
        this.currentStage = stage;
        this.updatedAt = now;
    }

    public void complete(String responseText, UUID transactionId, OffsetDateTime now) {
        this.status = AudioCommandStatus.COMPLETED;
        this.responseText = responseText;
        this.transactionId = transactionId;
        this.updatedAt = now;
    }

    public void fail(AiIntegrationException.Stage stage, OffsetDateTime now) {
        this.status = AudioCommandStatus.FAILED;
        this.failureStage = stage;
        this.updatedAt = now;
    }

    public void restartForRetry(OffsetDateTime now) {
        this.status = AudioCommandStatus.PROCESSING;
        this.failureStage = null;
        this.currentStage = AudioCommandStage.REGISTERED;
        this.updatedAt = now;
    }
}
