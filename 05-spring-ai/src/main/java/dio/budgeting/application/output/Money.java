package dio.budgeting.application.output;

import java.math.BigDecimal;

public final class Money {
    private Money() {
    }

    /** Converte um valor em centavos (ex.: 8000) para reais com duas casas (ex.: 80.00). */
    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
