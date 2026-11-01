package com.tukorea.gdgoc.koready.domain.candidate.service;

import com.tukorea.gdgoc.koready.domain.candidate.config.CandidateProperties;
import com.tukorea.gdgoc.koready.domain.candidate.dto.CandidateItem;
import com.tukorea.gdgoc.koready.domain.candidate.dto.CandidatePage;
import com.tukorea.gdgoc.koready.domain.candidate.dto.CandidateQuery;
import com.tukorea.gdgoc.koready.domain.candidate.dto.CandidateReason;
import com.tukorea.gdgoc.koready.domain.candidate.repository.CandidateRepository;
import com.tukorea.gdgoc.koready.domain.meta.entity.Category;
import com.tukorea.gdgoc.koready.domain.place.dto.PlaceSummary;
import com.tukorea.gdgoc.koready.domain.place.entity.SignalType;
import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripCategory;
import com.tukorea.gdgoc.koready.domain.trip.service.TripService;
import com.tukorea.gdgoc.koready.global.dto.Code;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.dto.PageResponse;
import com.tukorea.gdgoc.koready.global.exception.FieldErrors;
import com.tukorea.gdgoc.koready.global.util.SearchText;
import com.tukorea.gdgoc.koready.global.web.Language;
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
public class CandidateService {

	private final TripService tripService;
	private final CandidateRepository candidates;
	private final CandidateProperties properties;

	private static final List<String> ALL_CATEGORIES = Arrays.stream(Category.values()).map(Category::name).toList();

	@Transactional(readOnly = true)
	public CandidatePage find(Long userId, Long tripId, CandidateQuery query, Language language) {
		Trip trip = tripService.findMyTrip(userId, tripId);
		Set<String> chosen = trip.getCategories().stream()
				.map(TripCategory::getClass1Code)
				.collect(Collectors.toSet());
		// 추천 목록의 카테고리이자 필터로 고를 수 있는 카테고리: 고른 카테고리, 하나도 고르지 않았으면 8개 전체
		Collection<String> listed = chosen.isEmpty() ? ALL_CATEGORIES : chosen;
		FieldErrors errors = new FieldErrors();
		if (query.category() != null && !listed.contains(query.category().name())) {
			errors.add("category", "이 여행에서 고른 카테고리가 아닙니다");
		}
		// 검색이면 앞뒤 공백을 뺀 검색어, 추천 목록이면 null
		String searchText = query.query() == null ? null
				: SearchText.strip(query.query(), "query", CandidateQuery.MAX_QUERY_LENGTH, errors);
		errors.throwIfAny();
		Collection<String> categories = query.category() != null ? List.of(query.category().name()) : listed;
		CandidateRepository.Criteria criteria = new CandidateRepository.Criteria(trip.getId(), trip.getStartDate(),
				trip.getEndDate(), categories, searchText, properties.signalWeightM());
		List<CandidateItem> items = candidates.find(criteria, query.offset(), query.size()).stream()
				.map(row -> item(row, chosen, language))
				.toList();
		long totalCount = candidates.count(criteria);
		return CandidatePage.of(guide(trip.getDays()),
				PageResponse.of(items, query.page(), query.size(), totalCount));
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
		// 태그는 응답에 보이는 거리(m, 반올림)로 정한다 (1000.4m가 "1000m"인데 1km 태그가 빠지지 않게)
		long distanceM = Math.round(row.distanceM());
		if (distanceM <= properties.nearStayM()) {
			reasons.add(CandidateReason.NEAR_STAY);
		}
		List<SignalType> signals = row.signals().stream()
				.map(SignalType::valueOf)
				.sorted(Comparator.naturalOrder())
				.toList();
		return new CandidateItem(row.rank(), place, distanceM, reasons, signals, row.inTrip());
	}
}
