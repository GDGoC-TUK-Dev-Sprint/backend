package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.place.dto.PlaceSummary;
import com.tukorea.gdgoc.koready.domain.place.entity.Place;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripDayResponse;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripResponse;
import com.tukorea.gdgoc.koready.domain.trip.entity.Leg;
import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripItem;
import com.tukorea.gdgoc.koready.global.dto.Code;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.dto.Money;
import com.tukorea.gdgoc.koready.global.web.Language;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * TripDay 엔티티 → TripDayResponse. 지연 로딩이 있어 트랜잭션 안에서 부른다.
 * 금액은 원화로 준다 (환율 환산 전).
 */
@Component
@RequiredArgsConstructor
class TripDayAssembler {

	private final OpeningHoursChecker openingHoursChecker;

	TripDayResponse assemble(TripDay day, Language language) {
		Trip trip = day.getTrip();
		TripResponse.Stay tripStay = TripService.stay(trip, language);
		Set<Long> outsideHours = openingHoursChecker.findItemsOutsideHours(day);

		List<TripDayResponse.Item> items = day.getItems().stream()
				.map(item -> toItem(item, outsideHours, language))
				.toList();
		List<TripDayResponse.LegResponse> legs = day.getLegs().stream()
				.map(leg -> toLeg(leg, tripStay.location()))
				.toList();
		return new TripDayResponse(
				day.getDayNumber(),
				day.getTripDate(),
				day.getDepartTime(),
				day.getReturnTime(),
				summary(day),
				new TripDayResponse.Stay(tripStay.name(), tripStay.location()),
				items,
				legs);
	}

	private static TripDayResponse.Summary summary(TripDay day) {
		int totalStayMinutes = day.getItems().stream().mapToInt(TripItem::getStayMinutes).sum();
		int totalTravelMinutes = day.getLegs().stream().mapToInt(Leg::getDurationMinutes).sum();
		// 운임을 받지 못한 구간이 하나라도 있으면 합계도 null
		boolean fareUnknown = day.getLegs().stream().map(Leg::getFareKrw).anyMatch(Objects::isNull);
		Money fare = fareUnknown ? null : Money.krw(day.getLegs().stream().mapToLong(Leg::getFareKrw).sum());
		return new TripDayResponse.Summary(day.getItems().size(), totalStayMinutes, totalTravelMinutes, fare);
	}

	private static TripDayResponse.Item toItem(TripItem item, Set<Long> outsideHours, Language language) {
		List<TripDayResponse.Warning> warnings = outsideHours.contains(item.getId())
				? List.of(TripDayResponse.Warning.OUTSIDE_OPENING_HOURS)
				: List.of();
		return new TripDayResponse.Item(
				item.getId(),
				item.getSeq(),
				placeSummary(item.getPlace(), language),
				item.getOrigin(),
				item.getMealType(),
				item.getArriveTime(),
				item.getDepartTime(),
				item.getStayMinutes(),
				warnings);
	}

	/** 장소 요약. 동선 만들기의 넣지 못한 장소도 이것으로 만든다 */
	static PlaceSummary placeSummary(Place place, Language language) {
		return new PlaceSummary(
				place.getId(),
				language.pick(place.getNameKo(), place.getNameEn()),
				place.getNameKo(),
				new Code(place.getClass1Code(), language.pick(place.getClass1().getNameKo(), place.getClass1().getNameEn())),
				new Code(place.getClass3Code(), language.pick(place.getClass3().getNameKo(), place.getClass3().getNameEn())),
				PlaceSummary.Image.of(place.getImageUrl(), place.getThumbnailUrl(), place.getImageLicense()),
				LatLng.from(place.getLocation()));
	}

	private static TripDayResponse.LegResponse toLeg(Leg leg, LatLng stay) {
		return new TripDayResponse.LegResponse(
				leg.getSeq(),
				leg.getFromItem() == null ? null : leg.getFromItem().getId(),
				leg.getToItem() == null ? null : leg.getToItem().getId(),
				leg.getFromItem() == null ? stay : LatLng.from(leg.getFromItem().getPlace().getLocation()),
				leg.getToItem() == null ? stay : LatLng.from(leg.getToItem().getPlace().getLocation()),
				leg.getMode(),
				leg.getDurationMinutes(),
				leg.getDistanceM(),
				leg.getFareKrw() == null ? null : Money.krw(leg.getFareKrw()),
				leg.isEstimated());
	}
}
