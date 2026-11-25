package com.tukorea.gdgoc.koready.domain.placedetail.service;

import com.tukorea.gdgoc.koready.domain.exchange.service.ExchangeService;
import com.tukorea.gdgoc.koready.domain.favorite.repository.FavoriteRepository;
import com.tukorea.gdgoc.koready.domain.place.dto.PlaceSummary;
import com.tukorea.gdgoc.koready.domain.place.entity.HoursType;
import com.tukorea.gdgoc.koready.domain.place.entity.Place;
import com.tukorea.gdgoc.koready.domain.place.entity.PlaceFee;
import com.tukorea.gdgoc.koready.domain.place.entity.PlaceOpeningHour;
import com.tukorea.gdgoc.koready.domain.place.repository.PlaceFeeRepository;
import com.tukorea.gdgoc.koready.domain.place.repository.PlaceOpeningHourRepository;
import com.tukorea.gdgoc.koready.domain.place.repository.PlaceRepository;
import com.tukorea.gdgoc.koready.domain.place.service.OpeningHours;
import com.tukorea.gdgoc.koready.domain.placedetail.dto.PlaceDetailResponse;
import com.tukorea.gdgoc.koready.domain.trip.service.TripService;
import com.tukorea.gdgoc.koready.global.dto.Code;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.dto.Money;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import com.tukorea.gdgoc.koready.global.web.Language;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 상세. 장소에 찜·일정 포함·환율을 더하므로 place 도메인(다른 도메인을 쓰지 않는다) 밖에서 조합한다.
 */
@Service
@RequiredArgsConstructor
public class PlaceDetailService {

	private static final Comparator<PlaceOpeningHour> WEEKLY_ORDER = Comparator
			.comparing(PlaceOpeningHour::getDayOfWeek)
			.thenComparing(PlaceOpeningHour::getSeasonFrom, Comparator.nullsFirst(Comparator.naturalOrder()))
			.thenComparing(PlaceOpeningHour::getOpenTime);

	private final PlaceRepository places;
	private final PlaceOpeningHourRepository openingHours;
	private final PlaceFeeRepository fees;
	private final FavoriteRepository favorites;
	private final TripService tripService;
	private final ExchangeService exchangeService;
	private final Clock clock;

	/**
	 * 장소 상세. TourAPI에서 사라진 장소({@code active = false})도 준다.
	 *
	 * @param userId 로그인하지 않았으면 null (일정 포함 null, 찜 false)
	 * @param tripId 일정 포함을 볼 여행. 없는 여행·남의 여행이면 일정 포함이 null이다 (404 아님)
	 * @throws ApiException 없는 장소면 {@code 404 PLACE_NOT_FOUND}
	 */
	@Transactional(readOnly = true)
	public PlaceDetailResponse get(Long placeId, Long tripId, Long userId, Language language,
			DisplayCurrency currency) {
		Place place = places.findById(placeId).orElseThrow(() -> new ApiException(ErrorCode.PLACE_NOT_FOUND));
		PlaceSummary.Image image = PlaceSummary.Image.of(place.getImageUrl(), place.getThumbnailUrl(),
				place.getImageLicense());
		return new PlaceDetailResponse(
				place.getId(),
				language.pick(place.getNameKo(), place.getNameEn()),
				place.getNameKo(),
				new Code(place.getClass1Code(), language.pick(place.getClass1().getNameKo(), place.getClass1().getNameEn())),
				new Code(place.getClass3Code(), language.pick(place.getClass3().getNameKo(), place.getClass3().getNameEn())),
				LatLng.from(place.getLocation()),
				language.pick(place.getSummaryKo(), place.getSummaryEn()),
				image == null ? List.of() : List.of(image),
				place.getAddress(),
				hours(place, language),
				fees(place, language, currency),
				language.pick(place.getOverviewKo(), place.getOverviewEn()),
				userId == null || tripId == null ? null : inTrip(userId, tripId, placeId),
				userId != null && favorites.existsByUserIdAndPlaceId(userId, placeId));
	}

	private PlaceDetailResponse.Hours hours(Place place, Language language) {
		List<PlaceOpeningHour> rows = openingHours.findByPlaceIds(List.of(place.getId()));
		LocalDateTime at = LocalDateTime.now(clock);
		OpeningHours hours = OpeningHours.of(place, rows);
		OpeningHours.Now now = hours.now(at);
		List<String> closedDays = hours.closedDays(at.toLocalDate()).stream().map(PlaceDetailService::dayName).toList();
		return new PlaceDetailResponse.Hours(
				place.getHoursType(),
				new PlaceDetailResponse.Now(now.status(), now.closesAt(), now.lastEntryAt()),
				place.getHoursType() != HoursType.STRUCTURED ? List.of()
						: rows.stream().sorted(WEEKLY_ORDER).map(PlaceDetailService::weekly).toList(),
				closedDays,
				language.pick(place.getHoursTextKo(), place.getHoursTextEn()),
				language.pick(place.getRestTextKo(), place.getRestTextEn()));
	}

	private static PlaceDetailResponse.Weekly weekly(PlaceOpeningHour row) {
		return new PlaceDetailResponse.Weekly(dayName(row.getDayOfWeek()), row.getOpenTime(), row.getCloseTime(),
				row.getLastEntryTime(), row.getBreakStart(), row.getBreakEnd(), row.getSeasonFrom(), row.getSeasonTo());
	}

	/** 1 월 … 7 일 → {@code MON} … {@code SUN} */
	private static String dayName(int dayOfWeek) {
		return DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);
	}

	/** 요금. 외화 환율이 없으면 503 대신 원화 금액을 준다 */
	private PlaceDetailResponse.Fees fees(Place place, Language language, DisplayCurrency currency) {
		List<PlaceFee> rows = fees.findByPlaceIdOrderById(place.getId());
		Optional<BigDecimal> krwPerUnit = rows.isEmpty() ? Optional.empty() : exchangeService.findKrwPerUnit(currency);
		List<PlaceDetailResponse.FeeItem> items = rows.stream()
				.map(fee -> new PlaceDetailResponse.FeeItem(
						fee.getAudience(),
						language.pick(fee.getLabelKo(), fee.getLabelEn()),
						krwPerUnit.map(rate -> exchangeService.fromKrw(fee.getAmountKrw(), currency, rate))
								.orElseGet(() -> Money.krw(fee.getAmountKrw()))))
				.toList();
		return new PlaceDetailResponse.Fees(place.getFeeType(), items,
				language.pick(place.getFeeTextKo(), place.getFeeTextEn()));
	}

	private PlaceDetailResponse.InTrip inTrip(Long userId, Long tripId, Long placeId) {
		return tripService.findPlaceInMyTrip(userId, tripId, placeId)
				.map(item -> new PlaceDetailResponse.InTrip(item.tripId(), item.dayNumber(), item.date(),
						item.arriveTime()))
				.orElse(null);
	}
}
