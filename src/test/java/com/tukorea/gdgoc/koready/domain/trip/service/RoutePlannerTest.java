package com.tukorea.gdgoc.koready.domain.trip.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.domain.candidate.config.CandidateProperties;
import com.tukorea.gdgoc.koready.domain.trip.config.RouteProperties;
import com.tukorea.gdgoc.koready.domain.trip.dto.UnscheduledReason;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 동선 만들기 계산 (DB 없음). 숙소는 명동, 하루는 09:00~21:00, 하루 최대 6곳이다.
 * 위도 0.001 ≈ 110m라 0.002는 걸어서 가는 거리, 0.05(5.5km)는 다른 동네다.
 */
class RoutePlannerTest {

	private static final LatLng STAY = new LatLng(37.5636, 126.9826);
	/** 화·수·목 */
	private static final List<LocalDate> THREE_DAYS = List.of(
			LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12));
	private static final List<LocalDate> TWO_DAYS = THREE_DAYS.subList(0, 2);
	private static final List<LocalDate> ONE_DAY = THREE_DAYS.subList(0, 1);

	private final RouteProperties properties = new RouteProperties(LocalTime.of(9, 0), LocalTime.of(21, 0), 1000, 1.3,
			67, 333, 10);
	private final RoutePlanner planner = new RoutePlanner(new EstimatedLegRouter(properties), properties,
			new CandidateProperties(3000, 1000, 3, 5, 6));

	private long nextId = 1;

	@Test
	void sameInputGivesSamePlan() {
		List<RoutePlanner.Stop> stops = List.of(
				stop(0.05, 0.0), stop(0.051, 0.002), stop(-0.04, 0.01), stop(-0.041, 0.012), stop(0.002, 0.03),
				stop(0.0, 0.031), stop(0.01, -0.02), stop(0.012, -0.021), stop(0.003, 0.0));

		assertThat(planner.plan(STAY, THREE_DAYS, stops)).isEqualTo(planner.plan(STAY, THREE_DAYS, stops));
	}

	@Test
	void separatedGroupsGoToTheirOwnDays() {
		List<RoutePlanner.Stop> north = List.of(stop(0.05, 0.0), stop(0.051, 0.001), stop(0.052, -0.001));
		List<RoutePlanner.Stop> south = List.of(stop(-0.05, 0.0), stop(-0.051, 0.001), stop(-0.052, -0.001));

		RoutePlanner.Plan plan = planner.plan(STAY, TWO_DAYS, concat(north, south));

		assertThat(Set.of(ids(plan, 0), ids(plan, 1))).containsExactlyInAnyOrder(idSet(north), idSet(south));
		assertThat(plan.unscheduled()).isEmpty();
	}

	@Test
	void noDayGoesOverMaxPerDay() {
		List<RoutePlanner.Stop> stops = IntStream.range(0, 12).mapToObj(i -> stop(0.003 + i * 0.0002, 0.0)).toList();

		RoutePlanner.Plan plan = planner.plan(STAY, TWO_DAYS, stops);

		assertThat(plan.days()).allSatisfy(day -> assertThat(day).hasSize(6));
		assertThat(plan.unscheduled()).isEmpty();
	}

	@Test
	void farPlaceDoesNotTakeADayAlone() {
		// 동쪽 4km에 하나, 북쪽·남쪽 2km에 넷씩. 9곳·3일이면 날마다 2곳 이상이다
		RoutePlanner.Stop far = stop(0.0, 0.045);
		List<RoutePlanner.Stop> stops = new ArrayList<>(List.of(far));
		IntStream.range(0, 4).forEach(i -> stops.add(stop(0.02 + i * 0.0005, 0.0)));
		IntStream.range(0, 4).forEach(i -> stops.add(stop(-0.02 - i * 0.0005, 0.0)));

		RoutePlanner.Plan plan = planner.plan(STAY, THREE_DAYS, stops);

		assertThat(plan.days()).allSatisfy(day -> assertThat(day).hasSizeGreaterThanOrEqualTo(2));
		assertThat(plan.unscheduled()).isEmpty();
	}

	@Test
	void filledPlaceGivesWayToSelectedPlaceAndIsNotReported() {
		// 같은 시간에만 여는 두 곳 중 하나만 들어간다. 자동으로 채운 Q가 앞에 있어도 담은 P를 남긴다
		RoutePlanner.Stop q = filled(0.002, 0.0, 60, everyDay(LocalTime.of(9, 0), LocalTime.of(10, 30)));
		RoutePlanner.Stop p = stop(0.0025, 0.0, 60, everyDay(LocalTime.of(9, 0), LocalTime.of(10, 30)));

		RoutePlanner.Plan plan = planner.plan(STAY, ONE_DAY, List.of(q, p));

		assertThat(ids(plan, 0)).containsExactly(p.placeId());
		assertThat(plan.unscheduled()).isEmpty();
	}

	@Test
	void filledPlaceStaysWhenTheSelectedPlaceIsTheCause() {
		// 담은 N은 22:00에 열어 하루 끝(21:00) 전에 못 간다. 채운 장소를 빼도 안 되니 N만 빠지고 채운 장소는 남는다
		RoutePlanner.Stop night = stop(0.002, 0.0, 60, everyDay(LocalTime.of(22, 0), LocalTime.of(23, 59)));
		RoutePlanner.Stop fillA = filled(0.003, 0.0, 60, OpeningHours.UNRESTRICTED);
		RoutePlanner.Stop fillB = filled(0.004, 0.0, 60, OpeningHours.UNRESTRICTED);

		RoutePlanner.Plan plan = planner.plan(STAY, ONE_DAY, List.of(night, fillA, fillB));

		assertThat(ids(plan, 0)).containsExactlyInAnyOrder(fillA.placeId(), fillB.placeId());
		assertThat(plan.unscheduled()).containsOnlyKeys(night.placeId());
	}

	@Test
	void groupsAreMatchedToDaysTheyAreOpen() {
		// 북쪽 하나는 화요일 휴무, 남쪽 하나는 수요일 휴무 → 북쪽 묶음은 수요일, 남쪽 묶음은 화요일
		RoutePlanner.Stop closedTuesday = stop(0.05, 0.0, closedOn(2));
		List<RoutePlanner.Stop> north = List.of(closedTuesday, stop(0.051, 0.001));
		RoutePlanner.Stop closedWednesday = stop(-0.05, 0.0, closedOn(3));
		List<RoutePlanner.Stop> south = List.of(closedWednesday, stop(-0.051, 0.001));

		RoutePlanner.Plan plan = planner.plan(STAY, TWO_DAYS, concat(north, south));

		assertThat(ids(plan, 0)).isEqualTo(idSet(south));
		assertThat(ids(plan, 1)).isEqualTo(idSet(north));
	}

	@Test
	void stopOnItsClosedDayMovesToAnOpenDay() {
		// 한 묶음 안에 화요일 휴무와 수요일 휴무가 같이 있으면 하나는 다른 날로 옮긴다
		List<RoutePlanner.Stop> north = List.of(stop(0.05, 0.0, closedOn(2)), stop(0.051, 0.001, closedOn(3)),
				stop(0.052, 0.0));
		List<RoutePlanner.Stop> south = List.of(stop(-0.05, 0.0), stop(-0.051, 0.001));

		RoutePlanner.Plan plan = planner.plan(STAY, TWO_DAYS, concat(north, south));

		assertEveryVisitIsOpen(plan, TWO_DAYS);
		assertThat(plan.unscheduled()).isEmpty();
	}

	@Test
	void waitsUntilOpeningTime() {
		RoutePlanner.Stop opensAtTen = stop(0.002, 0.0, everyDay(LocalTime.of(10, 0), LocalTime.of(18, 0)));

		RoutePlanner.Plan plan = planner.plan(STAY, ONE_DAY, List.of(opensAtTen));

		assertThat(plan.days().getFirst().getFirst().arrive()).isEqualTo(10 * 60);
	}

	@Test
	void twoPlacesWithTheSameShortHoursAreSplitAcrossDays() {
		// P·Q는 숙소에서 걸어서 10분 거리에 있고 09:00~10:30에만 열며 60분 머문다. 같은 날에는 둘 다 못 간다
		RoutePlanner.Stop p = stop(0.004, 0.0, 60, everyDay(LocalTime.of(9, 0), LocalTime.of(10, 30)));
		RoutePlanner.Stop q = stop(0.0045, 0.0, 60, everyDay(LocalTime.of(9, 0), LocalTime.of(10, 30)));
		List<RoutePlanner.Stop> north = List.of(p, q, stop(0.005, 0.0));
		List<RoutePlanner.Stop> south = List.of(stop(-0.05, 0.0), stop(-0.051, 0.0));

		RoutePlanner.Plan plan = planner.plan(STAY, TWO_DAYS, concat(north, south));

		assertThat(plan.unscheduled()).isEmpty();
		assertThat(dayOf(plan, p)).isNotEqualTo(dayOf(plan, q));
		assertEveryVisitIsOpen(plan, TWO_DAYS);
	}

	@Test
	void secondPlaceWithTheSameShortHoursIsUnscheduledOnADayTrip() {
		RoutePlanner.Stop p = stop(0.002, 0.0, 60, everyDay(LocalTime.of(9, 0), LocalTime.of(10, 30)));
		RoutePlanner.Stop q = stop(0.0025, 0.0, 60, everyDay(LocalTime.of(9, 0), LocalTime.of(10, 30)));

		RoutePlanner.Plan plan = planner.plan(STAY, ONE_DAY, List.of(p, q));

		assertThat(plan.days().getFirst()).hasSize(1);
		assertThat(plan.unscheduled()).hasSize(1).containsValue(UnscheduledReason.NOT_ENOUGH_TIME);
	}

	@Test
	void dayEndsBeforeDayEndTime() {
		// 150분짜리 6곳은 하루(12시간)에 다 못 넣는다
		List<RoutePlanner.Stop> stops = IntStream.range(0, 6).mapToObj(i -> stop(0.002 + i * 0.0005, 0.0, 150,
				OpeningHours.UNRESTRICTED)).toList();

		RoutePlanner.Plan plan = planner.plan(STAY, ONE_DAY, stops);

		List<RoutePlanner.Visit> day = plan.days().getFirst();
		assertThat(day).hasSizeLessThan(6);
		assertThat(day.getLast().depart()).isLessThanOrEqualTo(21 * 60);
		assertThat(plan.unscheduled()).hasSize(6 - day.size()).containsOnlyKeys(
				stops.stream().map(RoutePlanner.Stop::placeId).filter(id -> !ids(plan, 0).contains(id)).toList());
		assertThat(plan.unscheduled().values()).containsOnly(UnscheduledReason.NOT_ENOUGH_TIME);
	}

	@Test
	void placeClosedForTheWholeTripIsUnscheduled() {
		RoutePlanner.Stop sundayOnly = stop(0.002, 0.0, 60, new OpeningHours(true,
				List.of(row(7, LocalTime.of(9, 0), LocalTime.of(18, 0))), null, null));
		RoutePlanner.Stop afterTrip = stop(0.003, 0.0, 60, new OpeningHours(false, List.of(),
				LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3)));

		RoutePlanner.Plan plan = planner.plan(STAY, THREE_DAYS, List.of(sundayOnly, afterTrip));

		assertThat(plan.unscheduled()).containsEntry(sundayOnly.placeId(), UnscheduledReason.CLOSED_DURING_TRIP)
				.containsEntry(afterTrip.placeId(), UnscheduledReason.CLOSED_DURING_TRIP);
		assertThat(plan.days()).allSatisfy(day -> assertThat(day).isEmpty());
	}

	@Test
	void festivalGoesToADayInItsPeriod() {
		RoutePlanner.Stop festival = stop(0.002, 0.0, 60, new OpeningHours(false, List.of(),
				LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 20)));
		List<RoutePlanner.Stop> stops = List.of(stop(0.05, 0.0), stop(-0.05, 0.0), festival);

		RoutePlanner.Plan plan = planner.plan(STAY, THREE_DAYS, stops);

		assertThat(dayOf(plan, festival)).isEqualTo(2);
	}

	@Test
	void everyDayIsInThePlanEvenWithoutPlaces() {
		RoutePlanner.Plan plan = planner.plan(STAY, THREE_DAYS, List.of(stop(0.002, 0.0)));

		assertThat(plan.days()).hasSize(3);
		assertThat(plan.days().stream().mapToInt(List::size).sum()).isEqualTo(1);
	}

	@Test
	void timesAreChainedWithinTheDay() {
		RoutePlanner.Plan plan = planner.plan(STAY, ONE_DAY, List.of(stop(0.002, 0.0), stop(0.004, 0.0),
				stop(0.03, 0.0)));

		List<RoutePlanner.Visit> day = plan.days().getFirst();
		assertThat(day.getFirst().arrive()).isGreaterThan(9 * 60);
		for (int i = 1; i < day.size(); i++) {
			assertThat(day.get(i).arrive()).isGreaterThan(day.get(i - 1).depart());
		}
		assertThat(day).allSatisfy(visit -> assertThat(visit.depart() - visit.arrive())
				.isEqualTo(visit.stop().stayMinutes()));
	}

	// --- 만들기

	private RoutePlanner.Stop stop(double dLat, double dLng) {
		return stop(dLat, dLng, 60, OpeningHours.UNRESTRICTED);
	}

	private RoutePlanner.Stop stop(double dLat, double dLng, OpeningHours hours) {
		return stop(dLat, dLng, 60, hours);
	}

	private RoutePlanner.Stop stop(double dLat, double dLng, int stayMinutes, OpeningHours hours) {
		return new RoutePlanner.Stop(nextId++, new LatLng(STAY.lat() + dLat, STAY.lng() + dLng), stayMinutes, hours, false);
	}

	/** 추천순으로 자동으로 채운 장소 */
	private RoutePlanner.Stop filled(double dLat, double dLng, int stayMinutes, OpeningHours hours) {
		return new RoutePlanner.Stop(nextId++, new LatLng(STAY.lat() + dLat, STAY.lng() + dLng), stayMinutes, hours, true);
	}

	/** 09:00~18:00에 열고 그 요일(1 월 … 7 일)만 쉰다 */
	private static OpeningHours closedOn(int closedDayOfWeek) {
		List<OpeningHours.Row> rows = IntStream.rangeClosed(1, 7)
				.filter(dayOfWeek -> dayOfWeek != closedDayOfWeek)
				.mapToObj(dayOfWeek -> row(dayOfWeek, LocalTime.of(9, 0), LocalTime.of(18, 0)))
				.toList();
		return new OpeningHours(true, rows, null, null);
	}

	private static OpeningHours everyDay(LocalTime open, LocalTime close) {
		return new OpeningHours(true, IntStream.rangeClosed(1, 7).mapToObj(day -> row(day, open, close)).toList(), null,
				null);
	}

	private static OpeningHours.Row row(int dayOfWeek, LocalTime open, LocalTime close) {
		return new OpeningHours.Row(dayOfWeek, null, null, open, close, null);
	}

	private static List<RoutePlanner.Stop> concat(List<RoutePlanner.Stop> a, List<RoutePlanner.Stop> b) {
		List<RoutePlanner.Stop> all = new ArrayList<>(a);
		all.addAll(b);
		return all;
	}

	// --- 읽기

	private static Set<Long> ids(RoutePlanner.Plan plan, int day) {
		return plan.days().get(day).stream().map(visit -> visit.stop().placeId()).collect(Collectors.toSet());
	}

	private static Set<Long> idSet(List<RoutePlanner.Stop> stops) {
		return stops.stream().map(RoutePlanner.Stop::placeId).collect(Collectors.toSet());
	}

	private static int dayOf(RoutePlanner.Plan plan, RoutePlanner.Stop stop) {
		for (int day = 0; day < plan.days().size(); day++) {
			if (ids(plan, day).contains(stop.placeId())) {
				return day;
			}
		}
		return -1;
	}

	private static void assertEveryVisitIsOpen(RoutePlanner.Plan plan, List<LocalDate> dates) {
		for (int day = 0; day < dates.size(); day++) {
			LocalDate date = dates.get(day);
			assertThat(plan.days().get(day)).allSatisfy(visit -> assertThat(visit.stop().hours().canStay(date,
					LocalTime.MIDNIGHT.plusMinutes(visit.arrive()), LocalTime.MIDNIGHT.plusMinutes(visit.depart())))
					.isTrue());
		}
	}
}
