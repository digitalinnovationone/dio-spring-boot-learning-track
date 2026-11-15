package dio.budgeting.infrastructure.http;

import dio.budgeting.application.input.PersistTransactionInput;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    @Test
    void deveRetornar400ComAMensagemDeCadaCampoInvalido() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(new PersistTransactionInput("", 0, null));

        var response = new ApiExceptionHandler()
                .handleValidation(new ConstraintViolationException(violations));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("A descrição é obrigatória", response.getBody().get("description"));
        assertEquals("O valor deve ser maior que zero (em centavos)", response.getBody().get("amount"));
        assertEquals("A categoria é obrigatória", response.getBody().get("category"));
    }
}
