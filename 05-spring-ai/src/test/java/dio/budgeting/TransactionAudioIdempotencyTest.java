package dio.budgeting;

import dio.budgeting.infrastructure.ai.AiIntegrationException;
import dio.budgeting.infrastructure.ai.AiTransactionProcessor;
import dio.budgeting.infrastructure.ai.AiTransactionResult;
import dio.budgeting.infrastructure.http.audio.AudioFileValidator;
import dio.budgeting.infrastructure.idempotency.AudioCommandOperationEntity;
import dio.budgeting.infrastructure.idempotency.AudioCommandOperationRepository;
import dio.budgeting.infrastructure.idempotency.AudioCommandStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end idempotency tests for POST /transactions/ai (TASK-009): real
 * TransactionController, real AudioCommandIdempotencyService, real JPA/H2
 * persistence (same isolated database as TransactionRestIntegrationTest).
 * AiTransactionProcessor is mocked as a black box - its internal
 * transcription/chat/speech behavior is covered by AiTransactionProcessorTest;
 * this class only proves the idempotency contract itself. No real OpenAI call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionAudioIdempotencyTest {

    @MockitoBean
    AudioFileValidator audioFileValidator;

    @MockitoBean
    AiTransactionProcessor aiTransactionProcessor;

    @Autowired
    AudioCommandOperationRepository operationRepository;

    @Autowired
    MockMvc mockMvc;

    private static MockMultipartFile audioFile(byte[] content) {
        return new MockMultipartFile("file", "audio.mp3", "audio/mpeg", content);
    }

    // --- Header validation ---

    @Test
    void missingIdempotencyKey_returns400_andNeverCallsProcessor() throws Exception {
        doNothing().when(audioFileValidator).validate(any());

        mockMvc.perform(multipart("/transactions/ai").file(audioFile(new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Chave idempotente ausente"));

        verifyNoInteractions(aiTransactionProcessor);
    }

    @Test
    void invalidIdempotencyKeyFormat_returns400_andNeverCallsProcessor() throws Exception {
        doNothing().when(audioFileValidator).validate(any());

        mockMvc.perform(multipart("/transactions/ai").file(audioFile(new byte[]{1}))
                        .header("Idempotency-Key", "chave com espaço"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Chave idempotente inválida"));

        verifyNoInteractions(aiTransactionProcessor);
    }

    // --- New key ---

    @Test
    void newKey_processesNormally_andPersistsCompletedOperation() throws Exception {
        doNothing().when(audioFileValidator).validate(any());
        when(aiTransactionProcessor.process(any(), any()))
                .thenReturn(new AiTransactionResult(new byte[]{9, 9, 9}, "Transação registrada.", null));

        mockMvc.perform(multipart("/transactions/ai").file(audioFile(new byte[]{1, 2, 3}))
                        .header("Idempotency-Key", "key-new-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"));

        verify(aiTransactionProcessor).process(any(), any());
        var entity = operationRepository.findByIdempotencyKey("key-new-001").orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(AudioCommandStatus.COMPLETED);
        assertThat(entity.getResponseText()).isEqualTo("Transação registrada.");
    }

    // --- Same key, same file: replay ---

    @Test
    void sameKeySameFile_secondRequestReplays_withoutCallingProcessAgain() throws Exception {
        doNothing().when(audioFileValidator).validate(any());
        when(aiTransactionProcessor.process(any(), any()))
                .thenReturn(new AiTransactionResult(new byte[]{9, 9, 9}, "Transação registrada.", null));
        when(aiTransactionProcessor.regenerateAudio("Transação registrada."))
                .thenReturn(new byte[]{7, 7, 7});

        byte[] content = new byte[]{4, 5, 6};

        mockMvc.perform(multipart("/transactions/ai").file(audioFile(content))
                        .header("Idempotency-Key", "key-replay-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"));

        var replayResult = mockMvc.perform(multipart("/transactions/ai").file(audioFile(content))
                        .header("Idempotency-Key", "key-replay-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(replayResult).isEqualTo(new byte[]{7, 7, 7});
        verify(aiTransactionProcessor, times(1)).process(any(), any());
        verify(aiTransactionProcessor, times(1)).regenerateAudio("Transação registrada.");
    }

    // --- Same key, different file: conflict ---

    @Test
    void sameKeyDifferentFile_returns409_andNeverCallsProcessorAgain() throws Exception {
        doNothing().when(audioFileValidator).validate(any());
        when(aiTransactionProcessor.process(any(), any()))
                .thenReturn(new AiTransactionResult(new byte[]{9, 9, 9}, "Transação registrada.", null));

        mockMvc.perform(multipart("/transactions/ai").file(audioFile(new byte[]{1, 1, 1}))
                        .header("Idempotency-Key", "key-conflict-001"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/transactions/ai").file(audioFile(new byte[]{2, 2, 2}))
                        .header("Idempotency-Key", "key-conflict-001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Chave idempotente em conflito"))
                .andExpect(jsonPath("$.detail").value("A chave de idempotência já foi utilizada com outro arquivo."));

        verify(aiTransactionProcessor, times(1)).process(any(), any());
    }

    // --- In progress ---

    @Test
    void operationStillProcessing_returns409_andNeverCallsProcessor() throws Exception {
        doNothing().when(audioFileValidator).validate(any());
        byte[] content = new byte[]{8, 8, 8};
        String fingerprint = dio.budgeting.infrastructure.idempotency.AudioPayloadFingerprint.of(content);
        operationRepository.save(new AudioCommandOperationEntity("key-inprogress-001", fingerprint, OffsetDateTime.now()));

        mockMvc.perform(multipart("/transactions/ai").file(audioFile(content))
                        .header("Idempotency-Key", "key-inprogress-001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Operação em processamento"));

        verifyNoInteractions(aiTransactionProcessor);
    }

    // --- Failure and retry policy ---

    @Test
    void failureDuringTranscription_allowsRetryWithSameKey() throws Exception {
        doNothing().when(audioFileValidator).validate(any());
        when(aiTransactionProcessor.process(any(), any()))
                .thenThrow(new AiIntegrationException(AiIntegrationException.Stage.TRANSCRIPTION,
                        AiIntegrationException.Reason.PROVIDER_UNAVAILABLE, "indisponível"))
                .thenReturn(new AiTransactionResult(new byte[]{1}, "Transação registrada.", null));

        byte[] content = new byte[]{3, 3, 3};

        mockMvc.perform(multipart("/transactions/ai").file(audioFile(content))
                        .header("Idempotency-Key", "key-retry-transcription-001"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(multipart("/transactions/ai").file(audioFile(content))
                        .header("Idempotency-Key", "key-retry-transcription-001"))
                .andExpect(status().isOk());

        verify(aiTransactionProcessor, times(2)).process(any(), any());
    }

    @Test
    void failureDuringChat_blocksRetryWithSameKey() throws Exception {
        doNothing().when(audioFileValidator).validate(any());
        when(aiTransactionProcessor.process(any(), any()))
                .thenThrow(new AiIntegrationException(AiIntegrationException.Stage.CHAT,
                        AiIntegrationException.Reason.PROVIDER_UNAVAILABLE, "indisponível"));

        byte[] content = new byte[]{6, 6, 6};

        mockMvc.perform(multipart("/transactions/ai").file(audioFile(content))
                        .header("Idempotency-Key", "key-retry-chat-001"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(multipart("/transactions/ai").file(audioFile(content))
                        .header("Idempotency-Key", "key-retry-chat-001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Reprocessamento não permitido"));

        verify(aiTransactionProcessor, times(1)).process(any(), any());
    }
}
