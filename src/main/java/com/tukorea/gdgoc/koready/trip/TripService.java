package com.tukorea.gdgoc.koready.trip;

import com.tukorea.gdgoc.koready.common.ApiException;
import com.tukorea.gdgoc.koready.common.Code;
import com.tukorea.gdgoc.koready.common.DisplayCurrency;
import com.tukorea.gdgoc.koready.common.ErrorCode;
import com.tukorea.gdgoc.koready.common.FieldErrors;
import com.tukorea.gdgoc.koready.common.Language;
import com.tukorea.gdgoc.koready.common.LatLng;
import com.tukorea.gdgoc.koready.common.Money;
import com.tukorea.gdgoc.koready.common.SeoulTime;
import com.tukorea.gdgoc.koready.meta.Area;
import com.tukorea.gdgoc.koready.meta.Category;
import com.tukorea.gdgoc.koready.meta.Nationality;
import com.tukorea.gdgoc.koready.place.Place;
import com.tukorea.gdgoc.koready.place.PlaceClass;
import com.tukorea.gdgoc.koready.place.PlaceClassRepository;
import com.tukorea.gdgoc.koready.place.PlaceRepository;
import com.tukorea.gdgoc.koready.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 만들기와 여행 요약. 여행을 다루는 다른 API도 내 여행은 {@link #findMyTrip}으로 찾고,
 * 응답에 여행 요약을 넣을 때는 {@link #toResponse}를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class TripService {

	private static final String TICKET_NO = "TK-%04d";
	/** {@code budget_per_person_krw}(integer) 한도 */
	private static final BigDecimal MAX_BUDGET_KRW = BigDecimal.valueOf(Integer.MAX_VALUE);

	private final TripRepository trips;
	private final PlaceRepository places;
	private final PlaceClassRepository placeClasses;
	private final TripProperties properties;
	private final EntityManager entityManager;
	private final Clock clock;

	/**
	 * 여행을 만든다. 끝나지 않은 확정 여행이 있으면 거절하고, 남아 있던 확정 전 여행은 지운다.
	 * 같은 사용자의 요청이 겹치지 않게 사용자 행을 잠근다.
	 */
	@Transactional
	public TripResponse create(Long userId, TripRequest request, Language language) {
		LocalDate today = LocalDate.now(clock);
		TripConditions conditions = conditions(request, today);
		User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
		if (user == null) {
			throw new ApiException(ErrorCode.UNAUTHORIZED);
		}
		List<Long> activeTripIds = trips.findActiveTripIds(userId, today);
		if (!activeTripIds.isEmpty()) {
			throw new ApiException(ErrorCode.ACTIVE_TRIP_EXISTS, Map.of("activeTripId", activeTripIds.getFirst()));
		}
		trips.deleteDrafts(userId);
		Trip trip = trips.save(Trip.create(user, conditions));
		return toResponse(trip, language);
	}

	/** 내 여행의 요약 */
	@Transactional(readOnly = true)
	public TripResponse get(Long userId, Long tripId, Language language) {
		return toResponse(findMyTrip(userId, tripId), language);
	}

	/**
	 * 내 여행. 없거나 다른 사용자의 여행이면 {@code 404 TRIP_NOT_FOUND}다 (남의 여행이 있는지 알리지 않는다).
	 * 숙소·카테고리를 지연 로딩하므로 부르는 쪽 트랜잭션 안에서 쓴다.
	 */
	public Trip findMyTrip(Long userId, Long tripId) {
		// 사용자는 지연 로딩 프록시라 ID만 읽어 users를 조회하지 않는다
		return trips.findById(tripId)
				.filter(trip -> trip.getUser().getId().equals(userId))
				.orElseThrow(() -> new ApiException(ErrorCode.TRIP_NOT_FOUND));
	}

	/** 여행 요약. 숙소·카테고리를 지연 로딩하므로 트랜잭션 안에서 부른다. 희망 예산은 원화로 준다 (환율 환산 전) */
	public TripResponse toResponse(Trip trip, Language language) {
		Integer budget = trip.getBudgetPerPersonKrw();
		return new TripResponse(
				trip.getId(),
				TICKET_NO.formatted(trip.getId()),
				trip.getStatus(),
				trip.isEditable(),
				trip.progressOn(LocalDate.now(clock)),
				language.pick(trip.getTitleKo(), trip.getTitleEn()),
				nationality(trip.getNationalityCode(), language),
				trip.getStartDate(),
				trip.getEndDate(),
				trip.getDays() - 1,
				trip.getDays(),
				trip.getPartySize(),
				budget == null ? null : Money.krw(budget),
				categories(trip, language),
				stay(trip, language),
				trips.hasDays(trip.getId()),
				SeoulTime.of(trip.getConfirmedAt()));
	}

	/** 요청을 검사해 저장할 조건으로 바꾼다. 잘못된 입력을 모두 모아 400을 던진 뒤 숙소를 찾는다 */
	private TripConditions conditions(TripRequest request, LocalDate today) {
		FieldErrors errors = new FieldErrors();
		if (request.startDate().isBefore(today)) {
			errors.add("startDate", "오늘(서울) 이후여야 합니다");
		}
		if (request.endDate().isBefore(request.startDate())) {
			errors.add("endDate", "시작일과 같거나 뒤여야 합니다");
		} else if (Trip.countDays(request.startDate(), request.endDate()) > properties.maxDays()) {
			errors.add("endDate", "여행은 최대 " + properties.maxDays() + "일입니다");
		}
		if (request.partySize() < 1 || request.partySize() > properties.maxPartySize()) {
			errors.add("partySize", "1~" + properties.maxPartySize() + "명이어야 합니다");
		}
		Integer budgetKrw = budgetKrw(request.budgetPerPerson(), errors);
		TripRequest.Stay stay = request.stay();
		switch (stay.type()) {
			case PLACE -> {
				if (stay.placeId() == null) {
					errors.add("stay.placeId", "PLACE이면 필수입니다");
				}
			}
			case AREA -> {
				if (stay.areaCode() == null) {
					errors.add("stay.areaCode", "AREA이면 필수입니다");
				}
			}
			case PIN -> errors.add("stay.type", "지도 핀(PIN)은 아직 받지 않습니다");
		}
		errors.throwIfAny();
		Set<Category> categories = request.categories() == null ? Set.of() : request.categories();
		return new TripConditions(request.nationality(), request.startDate(), request.endDate(), request.partySize(),
				budgetKrw, categories, resolveStay(stay));
	}

	/** 희망 예산(원). 환율 환산이 생기기 전에는 원화만 받는다 */
	private static Integer budgetKrw(Money budget, FieldErrors errors) {
		if (budget == null) {
			return null;
		}
		if (budget.currency() != DisplayCurrency.KRW) {
			errors.add("budgetPerPerson.currency", "지금은 KRW만 받습니다");
			return null;
		}
		if (budget.amount().stripTrailingZeros().scale() > 0) {
			errors.add("budgetPerPerson.amount", "원 단위 정수여야 합니다");
			return null;
		}
		if (budget.amount().compareTo(MAX_BUDGET_KRW) > 0) {
			errors.add("budgetPerPerson.amount", "너무 큽니다");
			return null;
		}
		return budget.amount().intValueExact();
	}

	private TripConditions.Stay resolveStay(TripRequest.Stay stay) {
		return switch (stay.type()) {
			case PLACE -> TripConditions.Stay.of(places.findById(stay.placeId())
					.filter(place -> place.isLodging() && place.isActive())
					.orElseThrow(() -> new ApiException(ErrorCode.PLACE_NOT_FOUND, "없는 숙박 장소입니다")));
			case AREA -> TripConditions.Stay.of(stay.areaCode());
			case PIN -> throw new IllegalStateException("PIN은 요청 검사에서 거절한다");
		};
	}

	/** 국적. 목록에서 빠진 코드면 코드를 이름으로 준다 */
	private static Code nationality(String code, Language language) {
		return find(Nationality.class, code)
				.map(n -> new Code(n.name(), language.pick(n.getNameKo(), n.getNameEn())))
				.orElseGet(() -> new Code(code, code));
	}

	/** 고른 카테고리. 이름은 분류 코드표에서 읽고, 순서는 {@link Category} 선언 순서다 */
	private List<Code> categories(Trip trip, Language language) {
		Set<String> codes = trip.getCategories().stream()
				.map(TripCategory::getClass1Code)
				.collect(Collectors.toSet());
		if (codes.isEmpty()) {
			return List.of();
		}
		Map<String, PlaceClass> byCode = placeClasses.findAllById(codes).stream()
				.collect(Collectors.toMap(PlaceClass::getCode, Function.identity()));
		return Arrays.stream(Category.values())
				.map(category -> byCode.get(category.name()))
				.filter(Objects::nonNull)
				.map(c -> new Code(c.getCode(), language.pick(c.getNameKo(), c.getNameEn())))
				.toList();
	}

	private static TripResponse.Stay stay(Trip trip, Language language) {
		Place place = trip.getStayPlace();
		// 지도 핀이면 저장한 핀 주소, 목록에서 빠진 지역이면 저장한 지역 이름(국문)
		String nameKo = trip.getStayName();
		String nameEn = null;
		if (trip.getStayType() == StayType.PLACE) {
			nameKo = place.getNameKo();
			nameEn = place.getNameEn();
		} else if (trip.getStayType() == StayType.AREA) {
			Optional<Area> area = find(Area.class, trip.getStayAreaCode());
			nameKo = area.map(Area::getNameKo).orElse(nameKo);
			nameEn = area.map(Area::getNameEn).orElse(null);
		}
		return new TripResponse.Stay(trip.getStayType(), place == null ? null : place.getId(), trip.getStayAreaCode(),
				language.pick(nameKo, nameEn), nameKo, trip.getStayAddress(), LatLng.from(trip.getStayLocation()));
	}

	/** 저장된 코드의 상수. 국적·지역 목록이 잠정이라, 지난 여행에 목록에서 빠진 코드가 남아 있을 수 있다 */
	private static <E extends Enum<E>> Optional<E> find(Class<E> type, String code) {
		return Arrays.stream(type.getEnumConstants()).filter(constant -> constant.name().equals(code)).findFirst();
	}
}
