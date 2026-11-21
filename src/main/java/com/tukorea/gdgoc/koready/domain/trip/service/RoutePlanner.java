package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.candidate.config.CandidateProperties;
import com.tukorea.gdgoc.koready.domain.trip.config.RouteProperties;
import com.tukorea.gdgoc.koready.domain.trip.dto.UnscheduledReason;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 동선 만들기의 계산: 휴무 → 일자 배정 → 일자 안 방문 순서. DB 없이 좌표·체류시간·운영시간만으로 정한다.
 * 이동시간은 좌표 추정값이고({@link EstimatedLegRouter}, 순서를 정할 때는 경로 API를 부르지 않는다), 같은 입력이면 같은 결과다.
 * <ol>
 * <li>휴무: 여행 날짜 중 여는 날이 없는 장소는 넣지 않는다 ({@code CLOSED_DURING_TRIP})</li>
 * <li>일자 배정: 좌표 k-means(k = 일수와 장소 수 중 작은 값, 무작위 없음) → 하루 최대 수를 넘는 묶음은 중심에서 먼 장소부터
 * 가까운 묶음으로 → 장소가 적은 묶음은 다른 묶음의 가까운 장소를 받아 최소 수를 채움 → 묶음과 날짜를 그날 쉬는 장소가 가장 적게 짝짓기
 * → 그래도 쉬는 날에 놓인 장소는 여는 날 중 자리가 있는 가까운 날로</li>
 * <li>방문 순서: 영업시간 안에 머물고 하루 끝 시각 전에 숙소로 돌아오는 순서 중 숙소 도착이 가장 이른 것 (완전 탐색).
 * 그런 순서가 없으면 영업시간이 가장 빠듯한 장소부터(자동으로 채운 장소 먼저) 다른 날로 옮기고, 둘 날이 없으면 넣지 않는다
 * ({@code NOT_ENOUGH_TIME})</li>
 * </ol>
 * 자동으로 채운 장소({@link Stop#filled})는 담은 장소와 같이 배정하되, 못 넣어도 결과의 넣지 못한 장소에 넣지 않는다.
 * 시각은 숙소 출발(하루 시작) + 이동 + 체류를 이어 붙이고 여는 시각 전이면 기다린다. 저장할 때 {@link DayRecalculator}가 같은 규칙으로 다시 계산한다.
 */
@Component
@RequiredArgsConstructor
class RoutePlanner {

	private static final int MAX_ITERATIONS = 100;

	private final EstimatedLegRouter legRouter;
	private final RouteProperties properties;
	private final CandidateProperties candidateProperties;

	/**
	 * 동선에 넣을 장소.
	 *
	 * @param stayMinutes 체류시간 (장소의 체류시간)
	 * @param filled 담은 장소가 아니라 추천순으로 자동으로 채운 장소
	 */
	record Stop(long placeId, LatLng location, int stayMinutes, OpeningHours hours, boolean filled) {
	}

	/** 방문 하나. 시각은 그날 0시부터의 분이다 */
	record Visit(Stop stop, int arrive, int depart) {
	}

	/**
	 * 계산 결과.
	 *
	 * @param days 날짜마다 방문 순서 (장소가 없는 날은 빈 목록)
	 * @param unscheduled 넣지 못한 담은 장소 ID와 이유 (받은 순서, 자동으로 채운 장소는 빼고)
	 */
	record Plan(List<List<Visit>> days, Map<Long, UnscheduledReason> unscheduled) {
	}

	/** {@code stops}의 장소 ID는 서로 달라야 한다 */
	Plan plan(LatLng stay, List<LocalDate> dates, List<Stop> stops) {
		Map<Long, UnscheduledReason> unscheduled = new HashMap<>();
		List<Stop> opening = new ArrayList<>();
		for (Stop stop : stops) {
			if (dates.stream().anyMatch(stop.hours()::opensOn)) {
				opening.add(stop);
			} else {
				unscheduled.put(stop.placeId(), UnscheduledReason.CLOSED_DURING_TRIP);
			}
		}
		List<List<Stop>> days = assignDays(stay, dates, opening, unscheduled);
		List<List<Visit>> visits = orderDays(stay, dates, days, unscheduled);

		Map<Long, UnscheduledReason> inRequestOrder = new LinkedHashMap<>();
		stops.stream()
				.filter(stop -> !stop.filled() && unscheduled.containsKey(stop.placeId()))
				.forEach(stop -> inRequestOrder.put(stop.placeId(), unscheduled.get(stop.placeId())));
		return new Plan(visits, inRequestOrder);
	}

	// --- 일자 배정

	private List<List<Stop>> assignDays(LatLng stay, List<LocalDate> dates, List<Stop> stops,
			Map<Long, UnscheduledReason> unscheduled) {
		List<List<Stop>> days = new ArrayList<>();
		dates.forEach(date -> days.add(new ArrayList<>()));
		if (stops.isEmpty()) {
			return days;
		}
		List<Group> groups = cluster(stay, stops, Math.min(dates.size(), stops.size()));
		fitCapacity(groups);
		balance(groups, stops.size());
		int[] dateOf = matchDates(groups, dates);
		for (int g = 0; g < groups.size(); g++) {
			days.get(dateOf[g]).addAll(groups.get(g).stops);
		}
		moveOffClosedDays(stay, dates, days, unscheduled);
		return days;
	}

	/** 일자 배정 중인 묶음 */
	private static final class Group {

		private LatLng center;
		private final List<Stop> stops = new ArrayList<>();

		private Group(LatLng center) {
			this.center = center;
		}
	}

	/** 좌표 k-means. 중심을 정하면 각 장소를 가장 가까운 중심에 두고, 중심을 묶음의 가운데로 옮기기를 바뀌지 않을 때까지 되풀이한다 */
	private static List<Group> cluster(LatLng stay, List<Stop> stops, int k) {
		List<LatLng> centers = initialCenters(stay, stops, k);
		int[] assigned = new int[stops.size()];
		Arrays.fill(assigned, -1);
		for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
			boolean changed = false;
			for (int i = 0; i < stops.size(); i++) {
				int nearest = nearest(centers, stops.get(i).location());
				if (nearest != assigned[i]) {
					assigned[i] = nearest;
					changed = true;
				}
			}
			if (!changed) {
				break;
			}
			for (int c = 0; c < k; c++) {
				List<Stop> members = new ArrayList<>();
				for (int i = 0; i < stops.size(); i++) {
					if (assigned[i] == c) {
						members.add(stops.get(i));
					}
				}
				if (!members.isEmpty()) {
					centers.set(c, centroid(members, null));
				}
			}
		}
		List<Group> groups = centers.stream().map(Group::new).toList();
		for (int i = 0; i < stops.size(); i++) {
			groups.get(assigned[i]).stops.add(stops.get(i));
		}
		return groups;
	}

	/** 첫 중심은 숙소에서 가장 먼 장소, 다음부터는 이미 고른 중심들에서 가장 먼 장소다 (거리가 같으면 앞 장소) */
	private static List<LatLng> initialCenters(LatLng stay, List<Stop> stops, int k) {
		List<LatLng> centers = new ArrayList<>();
		double[] farness = stops.stream().mapToDouble(stop -> distance(stay, stop.location())).toArray();
		while (centers.size() < k) {
			int farthest = 0;
			for (int i = 1; i < stops.size(); i++) {
				if (farness[i] > farness[farthest]) {
					farthest = i;
				}
			}
			LatLng center = stops.get(farthest).location();
			for (int i = 0; i < stops.size(); i++) {
				double toCenter = distance(center, stops.get(i).location());
				farness[i] = centers.isEmpty() ? toCenter : Math.min(farness[i], toCenter);
			}
			centers.add(center);
		}
		return centers;
	}

	/** 가장 가까운 중심 (같으면 앞 중심) */
	private static int nearest(List<LatLng> centers, LatLng location) {
		int nearest = 0;
		for (int c = 1; c < centers.size(); c++) {
			if (distance(centers.get(c), location) < distance(centers.get(nearest), location)) {
				nearest = c;
			}
		}
		return nearest;
	}

	/**
	 * 하루 최대 수를 넘는 묶음에서 중심에서 먼 장소부터 자리가 있는 가장 가까운 묶음으로 옮긴다.
	 * 장소 수가 일수 × 하루 최대 수 이하라 자리는 늘 있다 (요청 검사).
	 */
	private void fitCapacity(List<Group> groups) {
		int max = candidateProperties.maxPerDay();
		for (Group group : groups) {
			while (group.stops.size() > max) {
				Stop farthest = group.stops.stream()
						.max(Comparator.comparingDouble(stop -> distance(group.center, stop.location())))
						.orElseThrow();
				Group target = groups.stream()
						.filter(other -> other != group && other.stops.size() < max)
						.min(Comparator.comparingDouble(other -> distance(other.center, farthest.location())))
						.orElseThrow();
				group.stops.remove(farthest);
				target.stops.add(farthest);
			}
		}
	}

	/**
	 * 장소가 적은 묶음에 다른 묶음의 가장 가까운 장소를 옮겨 묶음마다 최소 ⌊장소 수 ÷ 묶음 수⌋ − 1곳을 둔다
	 * (9곳·3일이면 하루 2곳 이상). 먼 장소 하나가 혼자 한 날을 차지하지 않게 한다. 주는 묶음은 옮긴 뒤에도 최소 수 이상이고,
	 * 받는 묶음은 최소 수보다 적으므로 하루 최대 수를 넘지 않는다.
	 */
	private static void balance(List<Group> groups, int stopCount) {
		int min = stopCount / groups.size() - 1;
		while (true) {
			Group small = groups.stream().filter(group -> group.stops.size() < min).findFirst().orElse(null);
			if (small == null) {
				return;
			}
			LatLng center = centroid(small.stops, small.center);
			Group donor = null;
			Stop nearest = null;
			for (Group group : groups) {
				if (group == small || group.stops.size() <= min) {
					continue;
				}
				for (Stop stop : group.stops) {
					if (nearest == null || distance(center, stop.location()) < distance(center, nearest.location())) {
						donor = group;
						nearest = stop;
					}
				}
			}
			donor.stops.remove(nearest);
			small.stops.add(nearest);
		}
	}

	/**
	 * 묶음마다 날짜 위치. 그날 쉬는 장소 수의 합이 가장 적은 짝을 날짜 부분집합 동적 계획법으로 찾는다
	 * (일수가 설정값 최대 일수 이하라 부분집합이 적다). 합이 같으면 앞 묶음을 앞 날짜에 둔다.
	 */
	private static int[] matchDates(List<Group> groups, List<LocalDate> dates) {
		int k = groups.size();
		int n = dates.size();
		int[][] closed = new int[k][n];
		for (int g = 0; g < k; g++) {
			for (int d = 0; d < n; d++) {
				LocalDate date = dates.get(d);
				closed[g][d] = (int) groups.get(g).stops.stream().filter(stop -> !stop.hours().opensOn(date)).count();
			}
		}
		int states = 1 << n;
		int[] cost = new int[states];
		int[] previous = new int[states];
		int[] lastDate = new int[states];
		Arrays.fill(cost, Integer.MAX_VALUE);
		cost[0] = 0;
		int best = -1;
		for (int used = 0; used < states; used++) {
			if (cost[used] == Integer.MAX_VALUE) {
				continue;
			}
			int group = Integer.bitCount(used);
			if (group == k) {
				if (best < 0 || cost[used] < cost[best]) {
					best = used;
				}
				continue;
			}
			for (int d = 0; d < n; d++) {
				int next = used | (1 << d);
				if (next != used && cost[used] + closed[group][d] < cost[next]) {
					cost[next] = cost[used] + closed[group][d];
					previous[next] = used;
					lastDate[next] = d;
				}
			}
		}
		int[] dateOf = new int[k];
		for (int used = best; used != 0; used = previous[used]) {
			dateOf[Integer.bitCount(used) - 1] = lastDate[used];
		}
		return dateOf;
	}

	/** 그날 쉬는 장소를 여는 날 중 자리가 있는 가장 가까운 날로 옮긴다. 둘 날이 없으면 넣지 않는다 */
	private void moveOffClosedDays(LatLng stay, List<LocalDate> dates, List<List<Stop>> days,
			Map<Long, UnscheduledReason> unscheduled) {
		for (int d = 0; d < days.size(); d++) {
			LocalDate date = dates.get(d);
			for (Stop stop : List.copyOf(days.get(d))) {
				if (stop.hours().opensOn(date)) {
					continue;
				}
				days.get(d).remove(stop);
				Integer target = nearestDay(stay, dates, days, stop, d, day -> true);
				if (target == null) {
					unscheduled.put(stop.placeId(), UnscheduledReason.NOT_ENOUGH_TIME);
				} else {
					days.get(target).add(stop);
				}
			}
		}
	}

	/**
	 * 장소를 옮길 날: 그 장소가 여는 날 중 자리가 있고 {@code accepts}를 지키는 날에서, 그날 장소들의 가운데(장소가 없으면 숙소)가
	 * 가장 가까운 날 (같으면 앞 날짜). 없으면 null
	 */
	private Integer nearestDay(LatLng stay, List<LocalDate> dates, List<List<Stop>> days, Stop stop, int except,
			IntPredicate accepts) {
		Integer best = null;
		double bestDistance = 0;
		for (int d = 0; d < days.size(); d++) {
			if (d == except || days.get(d).size() >= candidateProperties.maxPerDay()
					|| !stop.hours().opensOn(dates.get(d))) {
				continue;
			}
			double distance = distance(centroid(days.get(d), stay), stop.location());
			if ((best == null || distance < bestDistance) && accepts.test(d)) {
				best = d;
				bestDistance = distance;
			}
		}
		return best;
	}

	// --- 방문 순서

	/** 날짜마다 방문 순서. 지킬 수 있는 순서가 없는 날은 영업시간이 가장 빠듯한 장소부터 다른 날로 옮기거나 뺀다 */
	private List<List<Visit>> orderDays(LatLng stay, List<LocalDate> dates, List<List<Stop>> days,
			Map<Long, UnscheduledReason> unscheduled) {
		List<List<Visit>> visits = new ArrayList<>(Collections.nCopies(days.size(), List.of()));
		for (int d = 0; d < days.size(); d++) {
			List<Visit> order = bestOrder(stay, dates.get(d), days.get(d));
			while (order == null) {
				Stop tightest = tightest(stay, dates.get(d), days.get(d));
				days.get(d).remove(tightest);
				// 받는 날은 그 장소를 더해도 지킬 수 있는 순서가 있어야 한다 (한 번 받은 날은 끝까지 지킬 수 있다)
				Integer target = nearestDay(stay, dates, days, tightest, d,
						day -> bestOrder(stay, dates.get(day), with(days.get(day), tightest)) != null);
				if (target == null) {
					unscheduled.put(tightest.placeId(), UnscheduledReason.NOT_ENOUGH_TIME);
				} else {
					days.get(target).add(tightest);
					if (target < d) {
						visits.set(target, bestOrder(stay, dates.get(target), days.get(target)));
					}
				}
				order = bestOrder(stay, dates.get(d), days.get(d));
			}
			visits.set(d, order);
		}
		return visits;
	}

	/**
	 * 옮기거나 뺄 장소: 영업시간이 가장 빠듯한 곳(하루 시작~끝 시각 중 여는 시간이 가장 짧은 곳), 같으면 오래 머무는 곳, 같으면 뒤에 놓인 곳.
	 * 담은 장소를 먼저 지킨다: 자동으로 채운 장소를 모두 빼면 지킬 수 있는 날이면 채운 장소 중에서, 빼도 안 되면 원인이 담은 장소라 그중에서 고른다.
	 */
	private Stop tightest(LatLng stay, LocalDate date, List<Stop> dayStops) {
		List<Stop> selected = dayStops.stream().filter(stop -> !stop.filled()).toList();
		List<Stop> filled = dayStops.stream().filter(Stop::filled).toList();
		boolean filledAreTheCause = !filled.isEmpty() && bestOrder(stay, date, selected) != null;
		List<Stop> stops = filledAreTheCause || selected.isEmpty() ? filled : selected;
		int from = OpeningHours.minutes(properties.dayStart());
		int to = OpeningHours.minutes(properties.dayEnd());
		Comparator<Stop> tighter = Comparator.<Stop>comparingInt(stop -> stop.hours().openMinutesBetween(date, from, to))
				.thenComparing(Comparator.comparingInt(Stop::stayMinutes).reversed());
		Stop tightest = stops.getFirst();
		for (Stop stop : stops) {
			if (tighter.compare(stop, tightest) <= 0) {
				tightest = stop;
			}
		}
		return tightest;
	}

	/**
	 * 영업시간 안에 머물고 하루 끝 시각 전에 숙소로 돌아오는 순서 중 숙소 도착이 가장 이른 것 (같으면 앞에 놓인 장소를 먼저).
	 * 하루 장소가 적어(하루 최대 수) 가지치기한 완전 탐색으로 찾는다. 없으면 null, 장소가 없으면 빈 목록이다.
	 */
	private List<Visit> bestOrder(LatLng stay, LocalDate date, List<Stop> stops) {
		if (stops.isEmpty()) {
			return List.of();
		}
		OrderSearch search = new OrderSearch(date, stops, travelMinutes(stay, stops),
				OpeningHours.minutes(properties.dayEnd()));
		search.visit(0, 0, stops.size(), OpeningHours.minutes(properties.dayStart()));
		if (search.bestOrder == null) {
			return null;
		}
		List<Visit> visits = new ArrayList<>();
		for (int i = 0; i < stops.size(); i++) {
			Stop stop = stops.get(search.bestOrder[i]);
			visits.add(new Visit(stop, search.bestArrive[i], search.bestArrive[i] + stop.stayMinutes()));
		}
		return visits;
	}

	/** 지점 사이 이동시간(분). 위치 0~n-1은 장소, n은 숙소다 */
	private int[][] travelMinutes(LatLng stay, List<Stop> stops) {
		int n = stops.size();
		int[][] minutes = new int[n + 1][n + 1];
		for (int from = 0; from <= n; from++) {
			for (int to = 0; to <= n; to++) {
				if (from != to) {
					LatLng a = from == n ? stay : stops.get(from).location();
					LatLng b = to == n ? stay : stops.get(to).location();
					minutes[from][to] = legRouter.route(a, b).durationMinutes();
				}
			}
		}
		return minutes;
	}

	/** 방문 순서 깊이 우선 탐색. 지금까지 가장 이른 숙소 도착보다 늦게 떠나는 가지는 버린다 */
	private static final class OrderSearch {

		private final LocalDate date;
		private final List<Stop> stops;
		private final int[][] travel;
		private final int dayEnd;
		private final int[] order;
		private final int[] arrive;
		private int bestReturn = Integer.MAX_VALUE;
		private int[] bestOrder;
		private int[] bestArrive;

		private OrderSearch(LocalDate date, List<Stop> stops, int[][] travel, int dayEnd) {
			this.date = date;
			this.stops = stops;
			this.travel = travel;
			this.dayEnd = dayEnd;
			this.order = new int[stops.size()];
			this.arrive = new int[stops.size()];
		}

		/** depth번째 방문을 고른다. used는 고른 장소 비트, last는 지금 위치(숙소면 장소 수), cursor는 지금 시각 */
		private void visit(int depth, int used, int last, int cursor) {
			int home = stops.size();
			if (depth == home) {
				int back = cursor + travel[last][home];
				if (back <= dayEnd && back < bestReturn) {
					bestReturn = back;
					bestOrder = order.clone();
					bestArrive = arrive.clone();
				}
				return;
			}
			for (int next = 0; next < home; next++) {
				if ((used & (1 << next)) != 0) {
					continue;
				}
				Stop stop = stops.get(next);
				Integer start = stop.hours().earliestStart(date, cursor + travel[last][next], stop.stayMinutes());
				if (start == null) {
					continue;
				}
				int depart = start + stop.stayMinutes();
				if (depart > dayEnd || depart >= bestReturn) {
					continue;
				}
				order[depth] = next;
				arrive[depth] = start;
				visit(depth + 1, used | (1 << next), next, depart);
			}
		}
	}

	// --- 좌표

	private static double distance(LatLng a, LatLng b) {
		return EstimatedLegRouter.distanceMeters(a, b);
	}

	/** 장소들의 가운데 (위도·경도 평균). 장소가 없으면 fallback */
	private static LatLng centroid(List<Stop> stops, LatLng fallback) {
		if (stops.isEmpty()) {
			return fallback;
		}
		double lat = stops.stream().mapToDouble(stop -> stop.location().lat()).average().orElseThrow();
		double lng = stops.stream().mapToDouble(stop -> stop.location().lng()).average().orElseThrow();
		return new LatLng(lat, lng);
	}

	private static List<Stop> with(List<Stop> stops, Stop added) {
		List<Stop> copy = new ArrayList<>(stops);
		copy.add(added);
		return copy;
	}
}
