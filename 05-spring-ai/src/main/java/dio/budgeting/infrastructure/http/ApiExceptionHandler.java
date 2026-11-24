package dio.budgeting.infrastructure.http;

import dio.budgeting.application.InvalidAudioException;
import dio.budgeting.application.InvalidPeriodException;
import dio.budgeting.domain.InvalidTransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({InvalidTransactionException.class, InvalidPeriodException.class,
            InvalidAudioException.class, IllegalArgumentException.class})
    ProblemDetail handleBadRequest(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requisição inválida", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        var detail = "Valor inválido para '" + exception.getName() + "': " + exception.getValue();
        var type = exception.getRequiredType();
        if (type != null && type.isEnum()) {
            detail += ". Valores aceitos: " + Arrays.toString(type.getEnumConstants());
        }
        return problem(HttpStatus.BAD_REQUEST, "Parâmetro inválido", detail);
    }

    @ExceptionHandler({TransientAiException.class, NonTransientAiException.class})
    ProblemDetail handleAiFailure(RuntimeException exception) {
        log.error("Falha ao chamar o provedor de IA", exception);
        return problem(HttpStatus.BAD_GATEWAY, "Falha no provedor de IA",
                "Não foi possível falar com o serviço de IA agora. Tente novamente em instantes.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
