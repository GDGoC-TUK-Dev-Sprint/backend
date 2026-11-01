package com.tukorea.gdgoc.koready.candidate;

import com.tukorea.gdgoc.koready.common.PageResponse;
import java.util.List;

/**
 * 장소 후보 응답: 권장 장소 수 안내 + 목록 응답({@code items·page·size·totalCount·hasNext}).
 */
public record CandidatePage(Guide guide, List<CandidateItem> items, int page, int size, long totalCount,
		boolean hasNext) {

	static CandidatePage of(Guide guide, PageResponse<CandidateItem> page) {
		return new CandidatePage(guide, page.items(), page.page(), page.size(), page.totalCount(), page.hasNext());
	}

	/**
	 * 권장 장소 수 안내 ("3일 일정에는 9~15곳을 추천해요. 하루 최대 6곳까지 넣을 수 있어요.").
	 *
	 * @param days 여행 일수
	 */
	public record Guide(int days, int recommendedMin, int recommendedMax, int maxPerDay) {
	}
}
