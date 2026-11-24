package dio.budgeting.application;

import dio.budgeting.application.output.AssistantOutput;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Orquestra o fluxo do assistente: áudio → texto → IA com tool calling → resposta (texto ou áudio).
 */
@Service
public class BudgetAssistantUseCase {
    static final String NOT_UNDERSTOOD_ANSWER =
            "Não consegui entender o áudio. Pode repetir informando o valor e onde foi o gasto?";

    private final ChatClient chatClient;
    private final TranscriptionModel transcriptionModel;
    private final TextToSpeechModel textToSpeechModel;
    private final Resource systemPrompt;
    private final Clock clock;

    public BudgetAssistantUseCase(ChatClient budgetChatClient,
                                  TranscriptionModel transcriptionModel,
                                  TextToSpeechModel textToSpeechModel,
                                  @Value("classpath:prompts/system-message.st") Resource systemPrompt,
                                  Clock clock) {
        this.chatClient = budgetChatClient;
        this.transcriptionModel = transcriptionModel;
        this.textToSpeechModel = textToSpeechModel;
        this.systemPrompt = systemPrompt;
        this.clock = clock;
    }

    /** Transcreve o áudio e responde ao comando. */
    public AssistantOutput answerAudio(Resource audio) {
        var transcription = transcriptionModel.transcribe(audio);
        if (transcription == null || transcription.isBlank()) {
            return new AssistantOutput("", NOT_UNDERSTOOD_ANSWER);
        }
        return answerText(transcription.strip());
    }

    /** Responde a um comando já em texto (útil para testar sem gravar áudio). */
    public AssistantOutput answerText(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("A mensagem não pode ser vazia");
        }
        var answer = chatClient.prompt()
                .system(system -> system.text(systemPrompt, StandardCharsets.UTF_8)
                .param("today", LocalDate.now(clock).toString()))
                .user(message)
                .call()
                .content();
        return new AssistantOutput(message, answer);
    }

    /** Converte a resposta em áudio MP3. */
    public byte[] speak(String text) {
        return textToSpeechModel.call(text);
    }
}
