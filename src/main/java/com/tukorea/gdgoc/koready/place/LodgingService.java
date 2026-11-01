package com.tukorea.gdgoc.koready.place;

import com.tukorea.gdgoc.koready.common.Code;
import com.tukorea.gdgoc.koready.common.FieldErrors;
import com.tukorea.gdgoc.koready.common.Language;
import com.tukorea.gdgoc.koready.common.LatLng;
import com.tukorea.gdgoc.koready.common.PageQuery;
import com.tukorea.gdgoc.koready.common.PageResponse;
import com.tukorea.gdgoc.koready.common.SearchText;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 숙소 위치 검색. 여행 숙소로 고를 수 있는 숙박 시설을 이름·주소로 찾는다.
 */
@Service
@RequiredArgsConstructor
class LodgingService {

	private static final int MAX_QUERY_LENGTH = 50;

	private final LodgingRepository lodgings;

	@Transactional(readOnly = true)
	public PageResponse<LodgingItem> search(String query, PageQuery page, Language language) {
		FieldErrors errors = new FieldErrors();
		String text = SearchText.strip(query, "query", MAX_QUERY_LENGTH, errors);
		errors.throwIfAny();
		List<LodgingItem> items = lodgings.find(text, page.offset(), page.size()).stream()
				.map(row -> item(row, language))
				.toList();
		return PageResponse.of(items, page, lodgings.count(text));
	}

	private static LodgingItem item(LodgingRepository.LodgingRow row, Language language) {
		return new LodgingItem(
				row.placeId(),
				language.pick(row.nameKo(), row.nameEn()),
				row.nameKo(),
				Place.address(row.addr1(), row.addr2()),
				new Code(row.class3Code(), language.pick(row.class3NameKo(), row.class3NameEn())),
				new LatLng(row.lat(), row.lng()));
	}
}
