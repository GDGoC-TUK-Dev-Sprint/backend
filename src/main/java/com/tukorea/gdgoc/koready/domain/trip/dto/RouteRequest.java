package com.tukorea.gdgoc.koready.domain.trip.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 동선 만들기 요청. 장소 선택에서 담은 장소 ID (1개 이상). 중복·장소 수·후보 범위는 {@code RouteService}가 검사한다.
 */
public record RouteRequest(@NotEmpty List<@NotNull Long> placeIds) {
}
