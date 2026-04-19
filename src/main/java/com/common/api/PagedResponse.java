package com.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages
) {
}
