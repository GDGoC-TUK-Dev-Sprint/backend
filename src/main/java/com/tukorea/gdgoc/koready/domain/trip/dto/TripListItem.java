package com.tukorea.gdgoc.koready.domain.trip.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.tukorea.gdgoc.koready.domain.place.dto.PlaceSummary;
import java.time.LocalDate;
import java.util.List;

/**
 * 여행 목록의 한 줄. 여행 요약({@link TripResponse})의 필드를 그대로 펼치고 일자별 요약과 지출 저장 여부를 더한다.
 *
 * @param daySummaries 메인 여행 티켓의 일자별 요약. 끝난 여행({@code PAST})이면 빈 배열
 * @param expenseSaved 실제 지출을 저장했는지 (이전 여행 기록에서 지출 입력으로 갈지 리포트로 갈지)
 */
public record TripListItem(@JsonUnwrapped TripResponse trip, List<DaySummary> daySummaries, boolean expenseSaved) {

	/**
	 * @param places 그날 주요 장소 이름. 식사 자리를 빼고 방문 순서 앞에서 3곳
	 * @param areaNames 그날 들르는 지역 이름 (방문 순서, 중복 없이)
	 * @param image 그날 대표 사진. 식사 자리를 뺀 장소 중 사진이 있는 첫 장소의 사진이고, 없으면 null
	 */
	public record DaySummary(int dayNumber, LocalDate date, List<String> places, List<String> areaNames,
			PlaceSummary.Image image) {
	}
}
