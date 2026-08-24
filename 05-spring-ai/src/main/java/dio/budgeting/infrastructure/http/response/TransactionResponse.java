package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.TransactionOutput;

import java.time.LocalDateTime;

public record TransactionResponse(
        String id,
        String category,
        String description,
        double amount,
        String audioRecordId,
        LocalDateTime createdAt) {

    public static TransactionResponse from(TransactionOutput output) {
        return new TransactionResponse(
                output.id(),
                output.category(),
                output.description(),
                output.value(),
                output.audioRecordId(),
                output.createdAt());
    }
}
