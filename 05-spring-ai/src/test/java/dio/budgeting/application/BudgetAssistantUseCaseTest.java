package dio.budgeting.application;

import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BudgetAssistantUseCaseTest {
    private final ChatClient chatClient = mock(ChatClient.class);
    private final TranscriptionModel transcriptionModel = mock(TranscriptionModel.class);
    private final BudgetAssistantUseCase useCase = new BudgetAssistantUseCase(
            chatClient, transcriptionModel, mock(TextToSpeechModel.class),
            new ByteArrayResource("prompt {today}".getBytes()), Clock.systemUTC());

    @Test
    void should_askToRepeat_without_callingTheModel_when_transcriptionIsBlank() {
        when(transcriptionModel.transcribe(any())).thenReturn("   ");

        var output = useCase.answerAudio(new ByteArrayResource(new byte[]{1, 2, 3}));

        assertThat(output.answer()).isEqualTo(BudgetAssistantUseCase.NOT_UNDERSTOOD_ANSWER);
        verifyNoInteractions(chatClient);
    }

    @Test
    void should_reject_when_messageIsBlank() {
        assertThatThrownBy(() -> useCase.answerText(" "))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(chatClient);
    }
}
