package com.tukorea.gdgoc.koready.domain.place.service;

import com.tukorea.gdgoc.koready.domain.place.entity.HoursType;
import com.tukorea.gdgoc.koready.domain.place.entity.Place;
import com.tukorea.gdgoc.koready.domain.place.entity.PlaceOpeningHour;
import com.tukorea.gdgoc.koready.domain.place.repository.PlaceOpeningHourRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 장소 하나의 영업시간 판정. 동선 만들기(일자 배정·순서), 시각 계산(여는 시각까지 기다림), 일자별 일정 경고가 같이 써서
 * 막 만든 동선에 경고가 붙지 않게 하고, 장소 상세의 "지금 운영 중"도 같은 행 고르기로 정한다.
 * 운영시간이 {@code STRUCTURED}가 아닌 장소(상시 개방·확인 필요)는 제약이 없다고 본다. 시각은 그날 0시부터의 분이다.
 *
 * @param type 운영시간 유형. {@code STRUCTURED}면 요일별 운영시간을 지킨다
 * @param rows 요일별 운영시간. 행이 없는 요일은 정기 휴무다
 * @param eventStart 축제·행사 시작일 (끝일은 {@code eventEnd}). 기간 밖의 날은 열지 않는다
 */
public record OpeningHours(HoursType type, List<Row> rows, LocalDate eventStart, LocalDate eventEnd) {

	public static final int MINUTES_PER_DAY = 24 * 60;
	/** 운영시간 제약이 없는 장소 */
	public static final OpeningHours UNRESTRICTED = new OpeningHours(HoursType.ALWAYS_OPEN, List.of(), null, null);
	private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");

	/** 장소의 운영시간. {@code rows}는 그 장소의 행이다 ({@code STRUCTURED}가 아니면 쓰지 않는다) */
	public static OpeningHours of(Place place, List<PlaceOpeningHour> rows) {
		HoursType type = place.getHoursType();
		return new OpeningHours(type, type == HoursType.STRUCTURED ? rows.stream().map(Row::from).toList() : List.of(),
				place.getEventStartDate(), place.getEventEndDate());
	}

	/** 장소마다 운영시간. {@code STRUCTURED}인 장소의 행만 한 번에 읽는다 */
	public static Map<Long, OpeningHours> byPlace(Collection<Place> places, PlaceOpeningHourRepository repository) {
		Set<Long> structured = places.stream()
				.filter(place -> place.getHoursType() == HoursType.STRUCTURED)
				.map(Place::getId)
				.collect(Collectors.toSet());
		Map<Long, List<PlaceOpeningHour>> rows = structured.isEmpty() ? Map.of()
				: repository.findByPlaceIds(structured).stream()
						.collect(Collectors.groupingBy(hour -> hour.getPlace().getId()));
		return places.stream().collect(Collectors.toMap(Place::getId,
				place -> of(place, rows.getOrDefault(place.getId(), List.of())), (a, b) -> a));
	}

	/** 그날 여는지. 축제·행사는 기간 안이어야 하고, {@code STRUCTURED}는 그 요일(계절 반영) 행이 있어야 한다 */
	public boolean opensOn(LocalDate date) {
		if ((eventStart != null && date.isBefore(eventStart)) || (eventEnd != null && date.isAfter(eventEnd))) {
			return false;
		}
		return !restricted() || !applicable(date).isEmpty();
	}

	/**
	 * arrive~depart 동안 머물 수 있는지 (일자별 일정 경고). 그 날짜에 드는 계절 행이 있으면 그것을, 없으면 연중 행을 쓴다.
	 * 축제·행사 기간은 보지 않는다 (동선 만들기가 기간 안의 날에만 둔다).
	 */
	public boolean canStay(LocalDate date, LocalTime arrive, LocalTime depart) {
		if (!restricted()) {
			return true;
		}
		int from = minutes(arrive);
		return covers(date, from, minutes(depart) < from ? minutes(depart) + MINUTES_PER_DAY : minutes(depart));
	}

	/**
	 * {@code arrive}에 도착해 {@code stayMinutes} 동안 머물 수 있는 가장 이른 시각. 여는 시각 전에 도착하면 여는 시각까지 기다린다.
	 * 그날 열지 않거나 기다려도 머물 수 없으면 null이다.
	 */
	public Integer earliestStart(LocalDate date, int arrive, int stayMinutes) {
		if (!opensOn(date)) {
			return null;
		}
		if (!restricted()) {
			return arrive;
		}
		List<Integer> starts = new ArrayList<>();
		starts.add(arrive);
		applicable(date).stream()
				.map(row -> minutes(row.open()))
				.filter(open -> open > arrive)
				.sorted()
				.forEach(starts::add);
		return starts.stream()
				.filter(start -> covers(date, start, start + stayMinutes))
				.findFirst()
				.orElse(null);
	}

	/** from~to 사이에 그날 여는 시간(분). 영업시간이 빠듯한 장소를 고를 때 쓴다. 제약이 없으면 from~to 전체다 */
	public int openMinutesBetween(LocalDate date, int from, int to) {
		if (!opensOn(date)) {
			return 0;
		}
		if (!restricted()) {
			return to - from;
		}
		return applicable(date).stream()
				.mapToInt(row -> {
					int open = minutes(row.open());
					int close = closeMinutes(row);
					return Math.max(0, Math.min(close, to) - Math.max(open, from));
				})
				.sum();
	}

	/**
	 * {@code at}에 운영 중인지 (장소 상세 "지금 운영 중"). 그날 행에 더해 전날 자정을 넘겨 영업하는 행도 본다.
	 * 상시 개방은 {@code OPEN}, 확인 필요는 {@code UNKNOWN}이고, 축제·행사 기간 밖이면 {@code CLOSED}다.
	 * 입장 마감이 지나면 닫는 시각 전이어도 {@code CLOSED}다 (일정 경고가 그 시각 도착을 머물 수 없다고 보는 것과 같다).
	 * 닫는 시각·입장 마감은 {@code OPEN}·{@code BREAK}일 때만 준다.
	 */
	public Now now(LocalDateTime at) {
		LocalDate date = at.toLocalDate();
		if (!restricted()) {
			if (!opensOn(date)) {
				return Now.CLOSED;
			}
			return new Now(type == HoursType.ALWAYS_OPEN ? Status.OPEN : Status.UNKNOWN, null, null);
		}
		int time = minutes(at.toLocalTime());
		if (opensOn(date)) {
			for (Row row : applicable(date)) {
				if (minutes(row.open()) <= time && admits(row, time)) {
					return now(row, time);
				}
			}
		}
		LocalDate yesterday = date.minusDays(1);
		if (opensOn(yesterday)) {
			for (Row row : applicable(yesterday)) {
				if (admits(row, time + MINUTES_PER_DAY)) {
					return now(row, time + MINUTES_PER_DAY);
				}
			}
		}
		return Now.CLOSED;
	}

	/** 그 행이 {@code time}(그 행의 날 0시부터의 분)을 덮을 때: 휴게시간이면 {@code BREAK}, 아니면 {@code OPEN} */
	private static Now now(Row row, int time) {
		Status status = Status.OPEN;
		if (row.breakStart() != null && row.breakEnd() != null) {
			int open = minutes(row.open());
			int breakStart = minutes(row.breakStart());
			int breakEnd = minutes(row.breakEnd());
			breakStart += breakStart < open ? MINUTES_PER_DAY : 0;
			breakEnd += breakEnd < open ? MINUTES_PER_DAY : 0;
			if (breakStart <= time && time < breakEnd) {
				status = Status.BREAK;
			}
		}
		return new Now(status, row.close(), row.lastEntry());
	}

	/**
	 * 그 날짜부터 한 주 동안 열지 않는 요일(1 월 … 7 일). 행이 없는 요일과, 계절 행만 있는데 그 계절이 아닌 요일이다.
	 * {@code STRUCTURED}가 아니면 비어 있다.
	 */
	public List<Integer> closedDays(LocalDate date) {
		if (!restricted()) {
			return List.of();
		}
		return date.datesUntil(date.plusDays(7))
				.filter(day -> applicable(day).isEmpty())
				.map(day -> day.getDayOfWeek().getValue())
				.sorted()
				.toList();
	}

	private boolean restricted() {
		return type == HoursType.STRUCTURED;
	}

	/** 그 날짜에 쓰는 행: 그 요일의 계절 행 중 날짜가 드는 것, 없으면 연중 행 */
	private List<Row> applicable(LocalDate date) {
		int dayOfWeek = date.getDayOfWeek().getValue();
		List<Row> sameDay = rows.stream().filter(row -> row.dayOfWeek() == dayOfWeek).toList();
		List<Row> seasonal = sameDay.stream().filter(row -> row.isSeasonal() && row.coversSeason(date)).toList();
		return seasonal.isEmpty() ? sameDay.stream().filter(row -> !row.isSeasonal()).toList() : seasonal;
	}

	private boolean covers(LocalDate date, int from, int to) {
		return applicable(date).stream().anyMatch(row -> covers(row, from, to));
	}

	/** from~to(분, to ≥ from) 동안 머물 수 있는지. 자정을 넘겨 영업하는 곳과 입장 마감 시각을 다룬다. 휴게시간은 아직 보지 않는다 */
	private static boolean covers(Row row, int from, int to) {
		int open = minutes(row.open());
		int close = closeMinutes(row);
		if (from < open && from + MINUTES_PER_DAY <= close) {
			// 자정 넘어 영업하는 곳에 자정 뒤에 도착
			from += MINUTES_PER_DAY;
			to += MINUTES_PER_DAY;
		}
		if (from < open || to > close) {
			return false;
		}
		if (row.lastEntry() == null) {
			return true;
		}
		int lastEntry = minutes(row.lastEntry());
		if (lastEntry < open) {
			lastEntry += MINUTES_PER_DAY;
		}
		return from <= lastEntry;
	}

	/** {@code time}(그 행의 날 0시부터의 분, 여는 시각 이후)에 들어갈 수 있는지: 닫는 시각 전이고 입장 마감을 넘기지 않았다 */
	private static boolean admits(Row row, int time) {
		if (time >= closeMinutes(row)) {
			return false;
		}
		if (row.lastEntry() == null) {
			return true;
		}
		int lastEntry = minutes(row.lastEntry());
		return time <= (lastEntry < minutes(row.open()) ? lastEntry + MINUTES_PER_DAY : lastEntry);
	}

	/** 닫는 시각(분). 자정을 넘기면 다음 날로 센다 */
	private static int closeMinutes(Row row) {
		int open = minutes(row.open());
		int close = minutes(row.close());
		return close <= open ? close + MINUTES_PER_DAY : close;
	}

	public static int minutes(LocalTime time) {
		return time.getHour() * 60 + time.getMinute();
	}

	/** 지금 운영 상태. {@code BREAK}는 휴게(준비)시간이다 */
	public enum Status {
		OPEN, BREAK, CLOSED, UNKNOWN
	}

	/**
	 * 지금 운영 상태와 그 행의 닫는 시각·입장 마감.
	 *
	 * @param closesAt {@code OPEN}·{@code BREAK}일 때 닫는 시각. 그 밖에는 null
	 * @param lastEntryAt {@code OPEN}·{@code BREAK}일 때 입장 마감. 없거나 그 밖에는 null
	 */
	public record Now(Status status, LocalTime closesAt, LocalTime lastEntryAt) {

		static final Now CLOSED = new Now(Status.CLOSED, null, null);
	}

	/**
	 * 요일 하나의 운영시간 ({@code place_opening_hours} 한 행).
	 *
	 * @param dayOfWeek 1 월 … 7 일
	 * @param seasonFrom MM-DD. null이면 연중
	 * @param close 자정을 넘기면 open보다 작다
	 * @param breakStart 휴게시간 시작. 동선 계산은 아직 보지 않고 "지금 운영 중"만 본다
	 */
	public record Row(int dayOfWeek, String seasonFrom, String seasonTo, LocalTime open, LocalTime close,
			LocalTime lastEntry, LocalTime breakStart, LocalTime breakEnd) {

		static Row from(PlaceOpeningHour hour) {
			return new Row(hour.getDayOfWeek(), hour.getSeasonFrom(), hour.getSeasonTo(), hour.getOpenTime(),
					hour.getCloseTime(), hour.getLastEntryTime(), hour.getBreakStart(), hour.getBreakEnd());
		}

		boolean isSeasonal() {
			return seasonFrom != null && seasonTo != null;
		}

		/** 날짜가 계절 구간에 드는지. 11-01~02-28처럼 해를 넘기는 구간도 다룬다 */
		boolean coversSeason(LocalDate date) {
			String monthDay = date.format(MONTH_DAY);
			if (seasonFrom.compareTo(seasonTo) <= 0) {
				return monthDay.compareTo(seasonFrom) >= 0 && monthDay.compareTo(seasonTo) <= 0;
			}
			return monthDay.compareTo(seasonFrom) >= 0 || monthDay.compareTo(seasonTo) <= 0;
		}
	}
}
