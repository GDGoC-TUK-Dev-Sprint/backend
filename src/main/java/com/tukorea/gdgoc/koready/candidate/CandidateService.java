package com.tukorea.gdgoc.koready.candidate;

import com.tukorea.gdgoc.koready.common.Code;
import com.tukorea.gdgoc.koready.common.FieldErrors;
import com.tukorea.gdgoc.koready.common.Language;
import com.tukorea.gdgoc.koready.common.LatLng;
import com.tukorea.gdgoc.koready.common.PageResponse;
import com.tukorea.gdgoc.koready.meta.Category;
import com.tukorea.gdgoc.koready.place.PlaceSummary;
import com.tukorea.gdgoc.koready.place.SignalType;
import com.tukorea.gdgoc.koready.trip.Trip;
import com.tukorea.gdgoc.koready.trip.TripCategory;
import com.tukorea.gdgoc.koready.trip.TripService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 선택의 추천 장소 목록과 장소 검색. 카테고리는 필터다 (고른 카테고리의 장소만, 하나도 고르지 않았으면 전체).
 */
@Service
@RequiredArgsConstructor
class CandidateService {

	private final TripService tripService;
	private final CandidateRepository candidates;
	private final CandidateProperties properties;

	@Transactional(readOnly = true)
	public CandidatePage find(Long userId, Long tripId, CandidateQuery query, Language language) {
		Trip trip = tripService.findMyTrip(userId, tripId);
		Set<String> chosen = trip.getCategories().stream()
				.map(TripCategory::getClass1Code)
				.collect(Collectors.toSet());
		if (query.category() != null && !chosen.contains(query.category().name())) {
			new FieldErrors().add("category", "이 여행에서 고른 카테고리가 아닙니다").throwIfAny();
		}
		CandidateRepository.Criteria criteria = new CandidateRepository.Criteria(trip.getId(), trip.getStartDate(),
				trip.getEndDate(), categories(query, chosen), query.searchText(), properties.signalWeightM());
		List<CandidateItem> items = candidates.find(criteria, query.offset(), query.size()).stream()
				.map(row -> item(row, chosen, language))
				.toList();
		long totalCount = candidates.count(criteria);
		return CandidatePage.of(guide(trip.getDays()),
				PageResponse.of(items, query.page(), query.size(), totalCount));
	}

	/** 추천 목록의 카테고리: 필터 하나, 없으면 고른 카테고리, 하나도 고르지 않았으면 8개 전체 */
	private static Collection<String> categories(CandidateQuery query, Set<String> chosen) {
		if (query.category() != null) {
			return List.of(query.category().name());
		}
		if (!chosen.isEmpty()) {
			return chosen;
		}
		return Arrays.stream(Category.values()).map(Category::name).toList();
	}

	private CandidatePage.Guide guide(int days) {
		return new CandidatePage.Guide(days, days * properties.recommendedMinPerDay(),
				days * properties.recommendedMaxPerDay(), properties.maxPerDay());
	}

	private CandidateItem item(CandidateRepository.CandidateRow row, Set<String> chosen, Language language) {
		PlaceSummary place = new PlaceSummary(
				row.placeId(),
				language.pick(row.nameKo(), row.nameEn()),
				row.nameKo(),
				new Code(row.class1Code(), language.pick(row.class1NameKo(), row.class1NameEn())),
				new Code(row.class3Code(), language.pick(row.class3NameKo(), row.class3NameEn())),
				PlaceSummary.Image.of(row.imageUrl(), row.thumbnailUrl(), row.imageLicense()),
				new LatLng(row.lat(), row.lng()));
		List<CandidateReason> reasons = new ArrayList<>();
		if (chosen.contains(row.class1Code())) {
			reasons.add(CandidateReason.CATEGORY_MATCH);
		}
		if (row.distanceM() <= properties.nearStayM()) {
			reasons.add(CandidateReason.NEAR_STAY);
		}
		List<SignalType> signals = row.signals().stream()
				.map(SignalType::valueOf)
				.sorted(Comparator.naturalOrder())
				.toList();
		return new CandidateItem(row.rank(), place, Math.round(row.distanceM()), reasons, signals, row.inTrip());
	}
}
