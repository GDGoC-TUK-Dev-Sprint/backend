package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.exchange.service.ExchangeService;
import com.tukorea.gdgoc.koready.domain.geo.dto.GeoReverseResponse;
import com.tukorea.gdgoc.koready.domain.geo.service.GeoService;
import com.tukorea.gdgoc.koready.domain.meta.entity.Area;
import com.tukorea.gdgoc.koready.domain.meta.entity.Category;
import com.tukorea.gdgoc.koready.domain.meta.entity.Nationality;
import com.tukorea.gdgoc.koready.domain.place.entity.Place;
import com.tukorea.gdgoc.koready.domain.place.entity.PlaceClass;
import com.tukorea.gdgoc.koready.domain.place.repository.PlaceClassRepository;
import com.tukorea.gdgoc.koready.domain.place.repository.PlaceRepository;
import com.tukorea.gdgoc.koready.domain.trip.config.TripProperties;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripRequest;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripResponse;
import com.tukorea.gdgoc.koready.domain.trip.entity.StayType;
import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripCategory;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripConditions;
import com.tukorea.gdgoc.koready.domain.trip.repository.PlaceInTrip;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripItemRepository;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripRepository;
import com.tukorea.gdgoc.koready.domain.user.entity.User;
import com.tukorea.gdgoc.koready.global.dto.Code;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.dto.Money;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import com.tukorea.gdgoc.koready.global.exception.FieldErrors;
import com.tukorea.gdgoc.koready.global.util.SeoulTime;
import com.tukorea.gdgoc.koready.global.web.Language;
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
 * 여행 만들기·조건 다시 저장·삭제·확정·여행 요약 (목록은 {@link TripListService}). 여행을 다루는 다른 API도 내 여행은 {@link #findMyTrip}으로 찾고,
 * 응답에 여행 요약을 넣을 때는 {@link #toResponse}를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class TripService {

	private static final String TICKET_NO = "TK-%04d";
	/** {@code budget_per_person_krw}(integer) 한도 */
	private static final BigDecimal MAX_BUDGET_KRW = BigDecimal.valueOf(Integer.MAX_VALUE);

	private final TripRepository trips;
	private final TripItemRepository tripItems;
	private final PlaceRepository places;
	private final PlaceClassRepository placeClasses;
	private final GeoService geoService;
	private final TripProperties properties;
	private final EntityManager entityManager;
	private final Clock clock;
	private final ExchangeService exchangeService;

	/**
	 * 여행을 만든다. 끝나지 않은 확정 여행이 있으면 거절하고, 남아 있던 확정 전 여행은 지운다.
	 * 같은 사용자의 요청이 겹치지 않게 사용자 행을 잠근다.
	 */
	@Transactional
	public TripResponse create(Long userId, TripRequest request, Language language, DisplayCurrency currency) {
		LocalDate today = LocalDate.now(clock);
		TripConditions conditions = conditions(request, today);
		User user = lockUser(userId);
		List<Long> activeTripIds = trips.findActiveTripIds(userId, today);
		if (!activeTripIds.isEmpty()) {
			throw new ApiException(ErrorCode.ACTIVE_TRIP_EXISTS, Map.of("activeTripId", activeTripIds.getFirst()));
		}
		trips.deleteDrafts(userId);
		Trip trip = trips.save(Trip.create(user, conditions));
		return toResponse(trip, language, currency);
	}

	/**
	 * 조건을 다시 저장한다 (요청 본문은 여행 만들기와 같고 전체를 바꾼다). 끝나지 않은 확정 여행 확인은 하지 않는다.
	 * 기간이나 숙소가 바뀌면 만든 동선(일자·일정 항목·이동 구간)을 지운다. 만들기처럼 사용자 행을 잠근다.
	 * 여행이 없거나(404) 확정했으면(409) 요청 값을 보기 전에 거절한다.
	 */
	@Transactional
	public TripResponse update(Long userId, Long tripId, TripRequest request, Language language,
			DisplayCurrency currency) {
		lockUser(userId);
		Trip trip = findMyTrip(userId, tripId);
		trip.requireEditable();
		TripConditions conditions = conditions(request, LocalDate.now(clock));
		if (trip.changeConditions(conditions)) {
			trips.deleteDays(trip.getId());
		}
		return toResponse(trip, language, currency);
	}

	/**
	 * 여행을 지운다. 상태와 상관없이 내 여행이면 지운다. 딸린 일자·일정 항목·이동 구간·고른 카테고리·확정 때의 예상 경비·실제 지출은
	 * DB가 함께 지운다 (ON DELETE CASCADE). 만들기·확정과 겹치지 않게 사용자 행을 잠근다.
	 */
	@Transactional
	public void delete(Long userId, Long tripId) {
		lockUser(userId);
		trips.delete(findMyTrip(userId, tripId));
	}

	/** 내 여행의 요약 */
	@Transactional(readOnly = true)
	public TripResponse get(Long userId, Long tripId, Language language, DisplayCurrency currency) {
		return toResponse(findMyTrip(userId, tripId), language, currency);
	}

	/**
	 * 내 여행. 없거나 다른 사용자의 여행이면 {@code 404 TRIP_NOT_FOUND}다 (남의 여행이 있는지 알리지 않는다).
	 * 숙소·카테고리를 지연 로딩하므로 부르는 쪽 트랜잭션 안에서 쓴다.
	 */
	public Trip findMyTrip(Long userId, Long tripId) {
		return myTrip(userId, tripId).orElseThrow(() -> new ApiException(ErrorCode.TRIP_NOT_FOUND));
	}

	/**
	 * 내 여행에 그 장소가 담긴 첫 일정 항목 (일자·순서 순). 없는 여행·남의 여행·담기지 않은 장소면 비어 있다.
	 * 장소 상세처럼 여행이 없어도 404가 아닌 조회가 쓴다.
	 */
	@Transactional(readOnly = true)
	public Optional<PlaceInTrip> findPlaceInMyTrip(Long userId, Long tripId, Long placeId) {
		return myTrip(userId, tripId)
				.flatMap(trip -> tripItems.findPlaceInTrip(tripId, placeId).stream().findFirst());
	}

	private Optional<Trip> myTrip(Long userId, Long tripId) {
		// 사용자는 지연 로딩 프록시라 ID만 읽어 users를 조회하지 않는다
		return trips.findById(tripId).filter(trip -> trip.getUser().getId().equals(userId));
	}

	/**
	 * 여행 요약. 숙소·카테고리를 지연 로딩하므로 트랜잭션 안에서 부른다. 희망 예산은 표시 통화로 환산해 준다
	 * (외화인데 환율이 없으면 {@code 503 EXCHANGE_RATE_UNAVAILABLE}).
	 */
	public TripResponse toResponse(Trip trip, Language language, DisplayCurrency currency) {
		Integer budget = trip.getBudgetPerPersonKrw();
		return response(trip, language, budget == null ? null : exchangeService.fromKrw(budget, currency),
				categories(trip, language, classNames(List.of(trip))), trips.hasDays(trip.getId()));
	}

	/**
	 * 여행 목록의 여행 요약들 (순서 그대로). 동선 유무·분류 이름·환율을 여행마다 읽지 않고 한 번씩만 읽는다.
	 * 목록은 환율이 없어도 줘야 하므로, 외화인데 환율이 없으면 희망 예산만 원화로 준다 (금액에 통화가 실려 있다).
	 * 지연 로딩이 있어 트랜잭션 안에서 부른다
	 */
	List<TripResponse> toResponses(List<Trip> list, Language language, DisplayCurrency currency) {
		if (list.isEmpty()) {
			return List.of();
		}
		Set<Long> withDays = trips.findTripIdsWithDays(list.stream().map(Trip::getId).toList());
		Map<String, PlaceClass> classNames = classNames(list);
		Optional<BigDecimal> krwPerUnit = exchangeService.findKrwPerUnit(currency);
		return list.stream()
				.map(trip -> {
					Integer budget = trip.getBudgetPerPersonKrw();
					Money budgetMoney = budget == null ? null
							: krwPerUnit.map(rate -> exchangeService.fromKrw(budget, currency, rate))
									.orElseGet(() -> Money.krw(budget));
					return response(trip, language, budgetMoney, categories(trip, language, classNames),
							withDays.contains(trip.getId()));
				})
				.toList();
	}

	private TripResponse response(Trip trip, Language language, Money budgetPerPerson, List<Code> categories,
			boolean routeCreated) {
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
				budgetPerPerson,
				categories,
				stay(trip, language),
				routeCreated,
				SeoulTime.of(trip.getConfirmedAt()));
	}

	/**
	 * 같은 사용자의 여행 요청(만들기·조건 다시 저장·동선 만들기·확정)이 겹치지 않게 사용자 행을 잠근다. 잠그지 않으면 겹친 요청끼리
	 * 한쪽 변경이 덮어써지거나 확정 전 여행이 둘이 될 수 있다. 사용자가 없으면(탈퇴) {@code 401}이다.
	 */
	User lockUser(Long userId) {
		User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
		if (user == null) {
			throw new ApiException(ErrorCode.UNAUTHORIZED);
		}
		return user;
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
		checkBudget(request.budgetPerPerson(), errors);
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
			case PIN -> {
				if (stay.location() == null) {
					errors.add("stay.location", "PIN이면 필수입니다");
				}
			}
		}
		errors.throwIfAny();
		Integer budgetKrw = budgetKrw(request.budgetPerPerson());
		Set<Category> categories = request.categories() == null ? Set.of() : request.categories();
		return new TripConditions(request.nationality(), request.startDate(), request.endDate(), request.partySize(),
				budgetKrw, categories, resolveStay(stay));
	}

	/**
	 * 희망 예산 형식 검사. 원·엔은 정수, 달러·유로는 소수 둘째 자리까지. 원화 환산은 다른 입력 오류를 다 모은 뒤 {@link #budgetKrw}에서 한다.
	 */
	private static void checkBudget(Money budget, FieldErrors errors) {
		if (budget == null) {
			return;
		}
		if (!budget.currency().fits(budget.amount())) {
			errors.add("budgetPerPerson.amount", switch (budget.currency()) {
				case KRW -> "원 단위 정수여야 합니다";
				case JPY -> "엔 단위 정수여야 합니다";
				case USD, EUR -> "소수 둘째 자리까지여야 합니다";
			});
		} else if (budget.amount().compareTo(MAX_BUDGET_KRW) > 0) {
			// 표시 통화는 모두 1단위가 1원 이상이라, 금액만으로 한도를 넘으면 원화로 바꿔도 넘는다
			errors.add("budgetPerPerson.amount", "너무 큽니다");
		}
	}

	/** 희망 예산(원). 외화는 오늘(서울) 환율로 바꾼다. 환율이 없으면 {@code 503 EXCHANGE_RATE_UNAVAILABLE} */
	private Integer budgetKrw(Money budget) {
		if (budget == null) {
			return null;
		}
		long krw = exchangeService.toKrw(budget);
		if (krw > Integer.MAX_VALUE) {
			new FieldErrors().add("budgetPerPerson.amount", "너무 큽니다").throwIfAny();
		}
		return (int) krw;
	}

	private TripConditions.Stay resolveStay(TripRequest.Stay stay) {
		return switch (stay.type()) {
			case PLACE -> TripConditions.Stay.of(places.findById(stay.placeId())
					.filter(place -> place.isLodging() && place.isActive())
					.orElseThrow(() -> new ApiException(ErrorCode.PLACE_NOT_FOUND, "없는 숙박 장소입니다")));
			case AREA -> TripConditions.Stay.of(stay.areaCode());
			case PIN -> pin(stay.location());
		};
	}

	/**
	 * 지도 핀. 핀 위치 주소 API와 같은 Reverse Geocoding으로 주소와 서울 여부를 다시 확인하고, 핀 주소를 숙소 이름·주소로 둔다.
	 * 서울 밖(바다·국외처럼 결과가 없는 곳 포함)이면 {@code 400 OUT_OF_SERVICE_AREA}, 호출이 실패하면 {@code 502 EXTERNAL_API_FAILED}다.
	 * 여행 저장과 한 트랜잭션이라 DB 연결을 잡은 채 기다리고, 조건 다시 저장은 사용자 행도 잠근 채 기다린다
	 * (네이버 시간 제한 안에서 같은 사용자의 다른 여행 요청만 기다린다. 잠그기 전에 부르면 확정·남의 여행보다 이 오류가 먼저 나온다).
	 */
	private TripConditions.Stay pin(LatLng location) {
		GeoReverseResponse reverse = geoService.reverse(location.lat(), location.lng());
		if (!reverse.inSeoul()) {
			throw new ApiException(ErrorCode.OUT_OF_SERVICE_AREA);
		}
		FieldErrors errors = new FieldErrors();
		if (reverse.address() == null) {
			// 서울 안인데 도로명·지번 주소가 모두 없는 곳. 숙소 이름으로 쓸 주소가 없다
			errors.add("stay.location", "주소가 없는 위치입니다");
		}
		errors.throwIfAny();
		return TripConditions.Stay.of(location.toPoint(), reverse.address());
	}

	/** 국적. 목록에서 빠진 코드면 코드를 이름으로 준다 */
	private static Code nationality(String code, Language language) {
		return find(Nationality.class, code)
				.map(n -> new Code(n.name(), language.pick(n.getNameKo(), n.getNameEn())))
				.orElseGet(() -> new Code(code, code));
	}

	/** 여행들이 고른 카테고리의 분류 (이름을 읽으려고). 고른 것이 없으면 조회하지 않는다 */
	private Map<String, PlaceClass> classNames(List<Trip> list) {
		Set<String> codes = list.stream()
				.flatMap(trip -> trip.getCategories().stream())
				.map(TripCategory::getClass1Code)
				.collect(Collectors.toSet());
		if (codes.isEmpty()) {
			return Map.of();
		}
		return placeClasses.findAllById(codes).stream()
				.collect(Collectors.toMap(PlaceClass::getCode, Function.identity()));
	}

	/** 고른 카테고리. 이름은 분류 코드표({@link #classNames})에서 읽고, 순서는 {@link Category} 선언 순서다 */
	private static List<Code> categories(Trip trip, Language language, Map<String, PlaceClass> classNames) {
		Set<String> codes = trip.getCategories().stream()
				.map(TripCategory::getClass1Code)
				.collect(Collectors.toSet());
		return Arrays.stream(Category.values())
				.filter(category -> codes.contains(category.name()))
				.map(category -> classNames.get(category.name()))
				.filter(Objects::nonNull)
				.map(c -> new Code(c.getCode(), language.pick(c.getNameKo(), c.getNameEn())))
				.toList();
	}

	/** 여행의 숙소. 일자별 일정(TripDayService)도 같은 이름·좌표를 쓴다 */
	static TripResponse.Stay stay(Trip trip, Language language) {
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
	/**
	 * [expense] 추가: 여행 확정 (API 명세 26). 새로 확정했으면 true, 이미 확정한 여행이면 아무것도 바꾸지 않고 false (그대로 200).
	 * 동선을 만들기 전이면 409 ROUTE_NOT_CREATED, 끝나지 않은 다른 확정 여행이 있으면 409 ACTIVE_TRIP_EXISTS (activeTripId).
	 */
	@Transactional
	public boolean confirm(Long userId, Long tripId) {
		lockUser(userId);
		Trip trip = findMyTrip(userId, tripId);
		if (!trip.isEditable()) {
			return false;
		}
		if (!trips.hasDays(trip.getId())) {
			throw new ApiException(ErrorCode.ROUTE_NOT_CREATED);
		}
		List<Long> otherActiveTripIds = trips.findActiveTripIds(userId, LocalDate.now(clock)).stream()
				.filter(id -> !id.equals(trip.getId()))
				.toList();
		if (!otherActiveTripIds.isEmpty()) {
			throw new ApiException(ErrorCode.ACTIVE_TRIP_EXISTS, Map.of("activeTripId", otherActiveTripIds.getFirst()));
		}
		trip.confirm(clock.instant());
		return true;
	}
}
