package com.tukorea.gdgoc.koready.domain.trip.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 순서 변경 요청. 그 일자의 일정 항목 ID 전부를 새 순서대로 보낸다.
 */
public record ReorderRequest(@NotNull List<@NotNull Long> itemIds) {
}
