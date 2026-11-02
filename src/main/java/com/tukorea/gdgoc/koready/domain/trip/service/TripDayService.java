package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.trip.dto.TripDayResponse;
import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripItem;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripDayRepository;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripItemRepository;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripRepository;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import com.tukorea.gdgoc.koready.global.web.Language;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일자별 일정 조회·순서 변경·장소 삭제. 순서를 바꾸거나 항목을 빼면 그 일자의 이동 구간과 도착·출발 시각을 다시 계산한다.
 * 편집은 같은 일자를 동시에 고치지 않게 일자 행을 잠근다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripDayService {

	private final TripService tripService;
	private final TripRepository trips;
	private final TripDayRepository days;
	private final TripItemRepository items;
	private final DayRecalculator recalculator;
	private final TripDayAssembler assembler;

	/** 일자별 일정. 동선을 만들기 전이면 {@code 409 ROUTE_NOT_CREATED}, 없는 일자면 {@code 404 DAY_NOT_FOUND}다 */
	public TripDayResponse getDay(Long userId, Long tripId, int dayNumber, Language language) {
		Trip trip = tripService.findMyTrip(userId, tripId);
		TripDay day = findDay(trip, dayNumber, false);
		return assembler.assemble(day, language);
	}

	/** 순서 변경. 항목 ID는 그대로 두고 seq만 바꾼 뒤 구간과 시각을 다시 계산한다 */
	@Transactional
	public TripDayResponse reorder(Long userId, Long tripId, int dayNumber, List<Long> itemIds, Language language) {
		Trip trip = tripService.findMyTrip(userId, tripId);
		trip.requireEditable();
		TripDay day = findDay(trip, dayNumber, true);

		Map<Long, TripItem> itemsById = day.getItems().stream()
				.collect(Collectors.toMap(TripItem::getId, Function.identity()));
		validateOrder(itemsById.keySet(), itemIds);

		day.clearLegs();
		items.flush();
		renumber(day, itemIds.stream().map(itemsById::get).toList());
		recalculator.rebuild(day, stayLocation(trip, language));
		return assembler.assemble(day, language);
	}

	/** 장소 삭제. 항목을 빼고 그 일자를 다시 계산한다. 마지막 항목을 지워도 일자는 남는다 */
	@Transactional
	public TripDayResponse deleteItem(Long userId, Long tripId, Long itemId, Language language) {
		Trip trip = tripService.findMyTrip(userId, tripId);
		trip.requireEditable();
		TripItem item = items.findInTrip(trip.getId(), itemId)
				.orElseThrow(() -> new ApiException(ErrorCode.ITEM_NOT_FOUND));
		TripDay day = days.findByIdForUpdate(item.getTripDay().getId())
				.orElseThrow(() -> new ApiException(ErrorCode.ITEM_NOT_FOUND));

		day.clearLegs();
		day.removeItem(item);
		items.flush();
		renumber(day, new ArrayList<>(day.getItems()));
		recalculator.rebuild(day, stayLocation(trip, language));
		return assembler.assemble(day, language);
	}

	/** 일자를 찾는다. 없으면 동선을 아직 만들지 않았는지에 따라 409 ROUTE_NOT_CREATED, 404 DAY_NOT_FOUND다 */
	private TripDay findDay(Trip trip, int dayNumber, boolean forUpdate) {
		Optional<TripDay> day = Optional.empty();
		// 일차는 smallint다. 범위를 벗어난 값은 없는 일자로 본다
		if (dayNumber >= 1 && dayNumber <= Short.MAX_VALUE) {
			Short number = (short) dayNumber;
			day = forUpdate ? days.findDayForUpdate(trip.getId(), number) : days.findDay(trip.getId(), number);
		}
		return day.orElseThrow(() -> new ApiException(
				trips.hasDays(trip.getId()) ? ErrorCode.DAY_NOT_FOUND : ErrorCode.ROUTE_NOT_CREATED));
	}

	private static LatLng stayLocation(Trip trip, Language language) {
		return TripService.stay(trip, language).location();
	}

	/** itemIds가 그 일자의 항목 ID 전부를 중복 없이 담고 있어야 한다 */
	private static void validateOrder(Set<Long> dayItemIds, List<Long> itemIds) {
		Set<Long> requested = new HashSet<>(itemIds);
		if (requested.size() != itemIds.size() || !requested.equals(dayItemIds)) {
			throw new ApiException(ErrorCode.ORDER_MISMATCH);
		}
	}

	/**
	 * (trip_day_id, seq)가 보통 UNIQUE 제약이라, 먼저 음수 임시 값으로 옮기고 flush한 뒤 1부터 새 값을 넣는다.
	 * 한 번에 바꾸면 번호가 겹치는 순간 제약 위반이 난다.
	 */
	private void renumber(TripDay day, List<TripItem> newOrder) {
		for (int i = 0; i < newOrder.size(); i++) {
			newOrder.get(i).changeSeq(-(i + 1));
		}
		items.flush();
		for (int i = 0; i < newOrder.size(); i++) {
			newOrder.get(i).changeSeq(i + 1);
		}
		day.sortItemsBySeq();
	}
}
