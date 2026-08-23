package dio.budgeting.infrastructure.http.response;

import dio.budgeting.domain.Category;

public record TotalByCategoryResponse(
        Category category,
        long total
) {
}