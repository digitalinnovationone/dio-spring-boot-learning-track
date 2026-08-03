package dio.budgeting.domain;

/**
 * Sinaliza a violação de uma invariante de uma transação financeira.
 * Garante que dados inválidos não sejam persistidos, independentemente do ponto
 * de entrada (REST ou execução via IA/Tool Calling).
 */
public class InvalidTransactionException extends RuntimeException {
    public InvalidTransactionException(String message) {
        super(message);
    }
}
