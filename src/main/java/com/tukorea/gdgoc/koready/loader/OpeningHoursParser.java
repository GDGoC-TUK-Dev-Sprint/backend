package com.tukorea.gdgoc.koready.loader;

import com.tukorea.gdgoc.koready.place.HoursType;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 운영시간·쉬는 날 원문을 요일별 시각으로 나눈다 ({@code place_opening_hours}).
 * 규칙으로 확실히 읽히는 문장만 나누고, 못 읽으면 {@code UNKNOWN}으로 두어 화면에 원문을 보여 준다.
 * 쉬는 날은 매주 반복되는 요일만 휴무로 읽는다. 공휴일·명절·"매월 둘째 일요일"·격주 같은 예외는 나누지 않는다 (원문으로 안내).
 */
final class OpeningHoursParser {

	/** dayOfWeek: 1 월 … 7 일. season: "MM-DD", 연중이면 null. 자정을 넘기면 close가 open보다 작다 */
	record Row(int dayOfWeek, String seasonFrom, String seasonTo, LocalTime open, LocalTime close,
			LocalTime lastEntry, LocalTime breakStart, LocalTime breakEnd) {
	}

	/** reason: UNKNOWN인 이유 (적재 로그용) */
	record Result(HoursType type, List<Row> rows, String reason) {

		static Result unknown(String reason) {
			return new Result(HoursType.UNKNOWN, List.of(), reason);
		}
	}

	/** 축제는 쉬는 날 필드가 없다. 기간(event_start/end_date) 안에서는 매일 같은 시간으로 본다 */
	private static final int FESTIVAL_TYPE = 15;

	private static final String WEEKDAY = "(?:월|화|수|목|금|토|일)";
	/** "월요일", "월" (뒤에 한글이 붙은 "일부"·"일반"은 요일이 아니다) */
	private static final String DAY = "(?:" + WEEKDAY + "요일|" + WEEKDAY + "(?![가-힣]))";
	private static final String DAY_WORD = "(?:매주|매일|연중무휴|평일|주중|주말|법정\\s*공휴일|공휴일|휴일|" + DAY + ")";
	private static final String DAY_SEP = "\\s*(?:~|,|·|ㆍ|・|/|및|와|과|\\s)\\s*";
	private static final Pattern DAYS_PREFIX =
			Pattern.compile("^(" + DAY_WORD + "(?:" + DAY_SEP + DAY_WORD + ")*)\\s*:?\\s*(?=\\d)");
	private static final Pattern DAY_ONE = Pattern.compile("^(" + WEEKDAY + ")(?:요일)?$");
	private static final Pattern DAY_RANGE = Pattern.compile("^(" + WEEKDAY + ")(?:요일)?~(" + WEEKDAY + ")(?:요일)?$");
	private static final Pattern DAY_TOKEN_SEP = Pattern.compile("\\s*(?:,|·|ㆍ|・|/|및|와|과|\\s)\\s*");

	/** "하절기(3~10월)", "3월~10월", "(11~2월)" */
	private static final Pattern SEASON = Pattern.compile(
			"^(?:(?:하절기|동절기|하계|동계|춘추계|춘추절기|여름철?|겨울철?|봄|가을)\\s*)?\\(?\\s*(\\d{1,2})\\s*월?\\s*~\\s*(\\d{1,2})\\s*월\\s*\\)?\\s*:?\\s*");

	private static final String TIME = "(?:(?:익일|다음\\s*날|새벽)\\s*)?(\\d{1,2}):(\\d{2})";
	private static final Pattern RANGE = Pattern.compile("^" + TIME + "\\s*~\\s*" + TIME);
	/** 점심·저녁 영업처럼 한 줄에 범위가 둘 ("11:30~14:30, 17:30~22:00") */
	private static final Pattern SECOND_RANGE = Pattern.compile("^\\s*[,/]\\s*" + TIME + "\\s*~\\s*" + TIME);
	private static final Pattern PAREN = Pattern.compile("^\\s*\\(([^()]*)\\)");
	private static final Pattern HEADER = Pattern.compile("^\\[(.+)]$");
	/** 입장·주문 마감. "마지막 주문 14:30, 20:30"처럼 둘이면 뒤의 것(저녁)을 쓴다 */
	private static final Pattern LAST_ENTRY = Pattern.compile(
			"^(?:저녁\\s*)?(?:입장\\s*(?:마감|종료)|마지막\\s*입장|최종\\s*입장|입장은|매표\\s*마감|발권\\s*마감|마지막\\s*주문|주문\\s*마감|라스트\\s*오더|L\\.?\\s*O\\.?)\\s*:?\\s*"
					+ "(?:\\d{1,2}:\\d{2}\\s*,\\s*)?" + TIME + "\\s*(?:까지)?$|^" + TIME
					+ "\\s*(?:입장\\s*마감|마지막\\s*입장|주문\\s*마감|마지막\\s*주문|라스트\\s*오더|까지\\s*입장)$",
			Pattern.CASE_INSENSITIVE);
	/** 기간·계절을 말하는 안내 ("※ 4월 1일~11월 30일에만 개관")는 운영시간을 바꿀 수 있어 못 읽음으로 둔다 */
	private static final Pattern PERIOD_NOTE =
			Pattern.compile("\\d{1,2}\\s*월\\s*(?:\\d{1,2}\\s*일\\s*)?~|~\\s*\\d{1,2}\\s*월|에만|동절기|하절기|임시\\s*휴");
	private static final Pattern SLASH_SPLIT = Pattern.compile("\\s*/\\s*");
	/** 점심 영업의 주문 마감 (준비시간 앞이라 행에 넣지 않는다) */
	private static final Pattern LUNCH_LAST_ORDER = Pattern.compile("^점심\\s*(?:마지막\\s*주문|주문\\s*마감|라스트\\s*오더)\\s*:?\\s*\\d{1,2}:\\d{2}$");
	private static final Pattern BREAK = Pattern.compile(
			"^(?:준비\\s*시간|브레이크\\s*타임|브레이크|휴게\\s*시간|점심\\s*시간)\\s*:?\\s*" + TIME + "\\s*~\\s*" + TIME + "$"
					+ "|^" + TIME + "\\s*~\\s*" + TIME + "\\s*(?:준비\\s*시간|브레이크\\s*타임|휴게\\s*시간)$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern ALWAYS = Pattern.compile(
			"^(?:연중무휴\\s*)?(?:매일\\s*)?(?:상시\\s*(?:개방|운영|이용|관람)?|24\\s*시간(?:\\s*(?:개방|운영|영업|이용))?|연중\\s*상시(?:\\s*개방)?)$");
	/** "평일 09:00~18:00, 주말 10:00~17:00"의 쉼표와 "주중 11:30~22:00주말 11:30~21:00"처럼 붙은 곳은 줄을 나눈다 */
	private static final Pattern DAY_COMMA =
			Pattern.compile("(?<=[\\d)])\\s*,\\s*(?=(?:평일|주중|주말|매일|" + DAY + ")[^\\n]*?\\d{1,2}:\\d{2})"
					+ "|(?<=[\\d)])\\s*(?=(?:평일|주중|주말|" + WEEKDAY + "요일)\\s*\\d{1,2}:\\d{2})");
	/** 요일 이름 없이 묶음 말만 쓴 요일 표현. 쉬는 날과 겹치면 쉬는 날이 빼 준다 ("평일 10:00~18:00" + "매주 월요일") */
	private static final Pattern BROAD_DAYS = Pattern.compile("^(?:\\s*(?:매주|매일|연중무휴|평일|주중|주말|법정\\s*공휴일|공휴일|휴일|및|와|과|,|·|/)\\s*)+$");
	private static final Pattern CLAUSE_SPLIT = Pattern.compile("\\s*,\\s*");

	private static final Pattern PARENS = Pattern.compile("\\([^()]*\\)");
	/** "토요일, 일요일을 제외한 법정공휴일"의 앞부분. 쉬는 요일이 아니라 공휴일 예외를 꾸민다 */
	private static final Pattern REST_EXCEPT_DAYS = Pattern.compile(
			"(?:" + DAY + "\\s*[,·및]\\s*)*" + DAY + "\\s*(?:을|를|은|는)\\s*제외(?:한|하고)?");
	private static final Pattern REST_SPLIT = Pattern.compile("\\s*(?:/|,|및)\\s*");
	/** "일요일과 공휴일" */
	private static final Pattern REST_AND = Pattern.compile("(?<=요일)\\s*(?:과|와)\\s+");
	/** 겹칠 때·뺄 때의 설명은 쉬는 요일이 아니다 ("일요일과 공휴일이 겹치는 경우 휴관") */
	private static final Pattern REST_CONDITION = Pattern.compile("겹치|겹칠|중복|제외");
	private static final Pattern REST_NONE =
			Pattern.compile("^(?:연중\\s*무휴|연중\\s*개방|연중\\s*운영|무휴|휴무\\s*없음|없음|상시\\s*개방)$");
	/** 매주 반복이 아닌 휴무: 요일이 있어도 예외로 본다 */
	private static final Pattern REST_IRREGULAR = Pattern.compile(
			"격주|첫째|둘째|셋째|넷째|다섯째|번째|마지막|매월|매달|매년|짝수|홀수");
	/** 공휴일·명절·특정 날짜 같은 예외 */
	private static final Pattern REST_EXCEPTION = Pattern.compile(
			"공휴일|휴일|국경일|대체|관공서|명절|설날|설|추석|신정|구정|연휴|당일|전날|전일|다음\\s*날|익일|기념일|선거|근로자|노동절|성탄|크리스마스"
					+ "|휴가|임시|인정|지정|정하는|정한|대관|행사|점검|정비|우천|기상|\\d+\\s*월\\s*\\d+\\s*일");
	/** 읽을 수 없는 휴무 안내. 계절 휴무("동절기 휴관")는 몇 달씩 닫을 수 있어 예외로 넘기지 않는다 */
	private static final Pattern REST_UNREADABLE =
			Pattern.compile("상이|부정기|비정기|불규칙|수시|홈페이지|문의|확인|참조|참고|예약|하계|동계|동절기|하절기");
	/** 요일 뒤에 조사가 붙으면("일요일을") 쉬는 요일이 아니다. 나머지는 띄어 쓴 뒤에만 온다 */
	private static final Pattern REST_WEEKLY = Pattern.compile(
			"^(?:매주\\s*)?((?:" + DAY + "|평일|주말|주중)(?:\\s*(?:~|·|ㆍ|・|\\s)\\s*(?:" + DAY + "|평일|주말|주중))*)"
					+ "(?:\\s*(?:정기\\s*)?(?:휴무|휴관|휴점|휴업|휴장|휴일)일?)?(?:\\s+(.*))?$");

	private static final int[] MONTH_DAYS = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

	private record Season(int from, int to) {

		Set<Integer> months() {
			Set<Integer> months = new TreeSet<>();
			for (int m = from; ; m = m % 12 + 1) {
				months.add(m);
				if (m == to) {
					return months;
				}
			}
		}
	}

	/** 요일 표현. broad: 요일 이름 없이 "평일·주말·매일"만 썼다 (쉬는 날이 빼 줘도 된다) */
	private record DaySpec(Set<Integer> days, boolean broad) {

		static DaySpec of(String expr) {
			Set<Integer> days = OpeningHoursParser.days(expr);
			return days == null ? null : new DaySpec(days, BROAD_DAYS.matcher(expr).matches());
		}
	}

	private static final class Entry {
		final Season season;
		final DaySpec days;
		final LocalTime open;
		LocalTime close;
		LocalTime lastEntry;
		LocalTime breakStart;
		LocalTime breakEnd;

		Entry(Season season, DaySpec days, LocalTime open, LocalTime close) {
			this.season = season;
			this.days = days;
			this.open = open;
			this.close = close;
		}
	}

	/** 못 읽음. 메시지가 UNKNOWN 이유 */
	private static final class Unreadable extends RuntimeException {
		Unreadable(String reason) {
			super(reason, null, false, false);
		}
	}

	private OpeningHoursParser() {
	}

	static Result parse(String hoursText, String restText, int contentType) {
		String hours = SourceText.normalize(hoursText);
		if (hours.isEmpty()) {
			return Result.unknown("운영시간 없음");
		}
		try {
			return parseHours(hours, restText, contentType);
		} catch (Unreadable e) {
			return Result.unknown(e.getMessage());
		}
	}

	private static Result parseHours(String hours, String restText, int contentType) {
		List<Entry> entries = new ArrayList<>();
		Entry last = null;
		Season headerSeason = null;
		DaySpec headerDays = null;
		boolean always = false;
		for (String line : SourceText.lines(DAY_COMMA.matcher(hours).replaceAll("\n"), true)) {
			if (SourceText.isNote(line)) {
				if (PERIOD_NOTE.matcher(line).find()) {
					throw new Unreadable("안내에 기간");
				}
				continue;
			}
			if (LUNCH_LAST_ORDER.matcher(line).matches()) {
				continue;
			}
			if (ALWAYS.matcher(line).matches()) {
				always = true;
				continue;
			}
			Matcher header = HEADER.matcher(line);
			if (header.matches()) {
				String inner = header.group(1).strip();
				Matcher season = SEASON.matcher(inner);
				DaySpec days = DaySpec.of(inner);
				if (season.matches()) {
					headerSeason = season(season);
					headerDays = null;
				} else if (days != null && !days.days().isEmpty()) {
					headerDays = days;
				} else {
					throw new Unreadable("머리말");
				}
				last = null;
				continue;
			}
			Matcher breakTime = BREAK.matcher(line);
			if (breakTime.matches()) {
				if (last == null || last.breakStart != null) {
					throw new Unreadable("준비시간");
				}
				setBreak(last, breakTime);
				continue;
			}
			Matcher lastEntry = LAST_ENTRY.matcher(line);
			if (lastEntry.matches()) {
				if (last == null) {
					throw new Unreadable("입장 마감");
				}
				last.lastEntry = lastEntryTime(lastEntry);
				continue;
			}
			Entry entry = rangeLine(line, headerSeason, headerDays);
			if (entry == null) {
				// 공휴일만의 운영시간은 예외라 나누지 않는다
				last = null;
				continue;
			}
			if (entry.open.equals(entry.close)) {
				if (!entry.open.equals(LocalTime.MIDNIGHT)) {
					throw new Unreadable("시각");
				}
				always = true;
				continue;
			}
			entries.add(entry);
			last = entry;
		}

		boolean noRest = restText == null || restText.isBlank();
		Set<Integer> closed = noRest ? Set.of() : closedDays(restText);
		if (always) {
			if (!entries.isEmpty()) {
				throw new Unreadable("상시와 시각");
			}
			if (!closed.isEmpty()) {
				throw new Unreadable("상시인데 휴무 요일");
			}
			return new Result(HoursType.ALWAYS_OPEN, List.of(), null);
		}
		if (entries.isEmpty()) {
			throw new Unreadable("시각 없음");
		}
		return new Result(HoursType.STRUCTURED, rows(entries, closed, noRest && contentType != FESTIVAL_TYPE), null);
	}

	/** 범위 줄: [계절] [요일] 시각~시각 [, 시각~시각] [(입장 마감·준비시간·요일)]. 공휴일만이면 null */
	private static Entry rangeLine(String line, Season headerSeason, DaySpec headerDays) {
		String rest = line;
		Season season = headerSeason;
		Matcher seasonPrefix = SEASON.matcher(rest);
		if (seasonPrefix.lookingAt() && seasonPrefix.end() < rest.length()
				&& (Character.isDigit(rest.charAt(seasonPrefix.end()))
						|| DAYS_PREFIX.matcher(rest.substring(seasonPrefix.end())).lookingAt())) {
			if (headerSeason != null) {
				throw new Unreadable("계절");
			}
			season = season(seasonPrefix);
			rest = rest.substring(seasonPrefix.end());
		}
		DaySpec days = headerDays;
		Matcher dayPrefix = DAYS_PREFIX.matcher(rest);
		if (dayPrefix.lookingAt()) {
			DaySpec parsed = DaySpec.of(dayPrefix.group(1));
			if (parsed == null || headerDays != null) {
				throw new Unreadable("요일");
			}
			days = parsed;
			rest = rest.substring(dayPrefix.end());
		}
		Matcher range = RANGE.matcher(rest);
		if (!range.lookingAt()) {
			throw new Unreadable("문장");
		}
		Entry entry = new Entry(season, days, time(range, 1, false), time(range, 3, true));
		rest = rest.substring(range.end());
		Matcher second = SECOND_RANGE.matcher(rest);
		if (second.lookingAt()) {
			LocalTime open2 = time(second, 1, false);
			if (!entry.open.isBefore(entry.close) || !entry.close.isBefore(open2)) {
				throw new Unreadable("시각 둘");
			}
			entry.breakStart = entry.close;
			entry.breakEnd = open2;
			entry.close = time(second, 3, true);
			rest = rest.substring(second.end());
		}
		DaySpec parenDays = null;
		Matcher paren;
		while ((paren = PAREN.matcher(rest)).lookingAt()) {
			String inner = paren.group(1).strip();
			// "(브레이크 타임 15:30~17:00, 라스트 오더 21:30)"처럼 쉼표·"/"로 이은 조건은 하나씩 본다
			List<String> clauses = new ArrayList<>();
			for (String piece : SLASH_SPLIT.split(inner)) {
				if (LAST_ENTRY.matcher(piece).matches()) {
					clauses.add(piece);
				} else {
					clauses.addAll(List.of(CLAUSE_SPLIT.split(piece)));
				}
			}
			for (String clause : clauses) {
				DaySpec clauseDays = parenClause(clause, entry);
				if (clauseDays != null) {
					if (clauseDays.days().isEmpty() || days != null || parenDays != null) {
						throw new Unreadable("괄호");
					}
					parenDays = clauseDays;
				}
			}
			rest = rest.substring(paren.end());
		}
		if (!rest.isBlank()) {
			throw new Unreadable("문장");
		}
		DaySpec entryDays = parenDays != null ? parenDays : days;
		if (entryDays != null && entryDays.days().isEmpty()) {
			return null;
		}
		return entryDays == entry.days ? entry : withDays(entry, entryDays);
	}

	/** 괄호 안 조건 하나. 입장 마감·준비시간이면 entry에 넣고 null, 요일이면 그 요일 */
	private static DaySpec parenClause(String clause, Entry entry) {
		Matcher lastEntry = LAST_ENTRY.matcher(clause);
		Matcher breakTime = BREAK.matcher(clause);
		if (lastEntry.matches()) {
			entry.lastEntry = lastEntryTime(lastEntry);
			return null;
		}
		if (breakTime.matches() && entry.breakStart == null) {
			setBreak(entry, breakTime);
			return null;
		}
		if (LUNCH_LAST_ORDER.matcher(clause).matches()) {
			return null;
		}
		DaySpec days = DaySpec.of(clause);
		if (days == null) {
			throw new Unreadable("괄호");
		}
		return days;
	}

	private static Entry withDays(Entry e, DaySpec days) {
		Entry copy = new Entry(e.season, days, e.open, e.close);
		copy.lastEntry = e.lastEntry;
		copy.breakStart = e.breakStart;
		copy.breakEnd = e.breakEnd;
		return copy;
	}

	/** 계절·요일별로 펼친다. 쉬는 날이 이긴다. 요일을 정하지 못하면 못 읽음 */
	private static List<Row> rows(List<Entry> entries, Set<Integer> closed, boolean noRest) {
		boolean seasonal = entries.stream().anyMatch(e -> e.season != null);
		if (seasonal && entries.stream().anyMatch(e -> e.season == null)) {
			throw new Unreadable("계절");
		}
		Map<Season, List<Entry>> bySeason = new LinkedHashMap<>();
		for (Entry e : entries) {
			bySeason.computeIfAbsent(e.season, k -> new ArrayList<>()).add(e);
		}
		if (seasonal) {
			Set<Integer> months = new TreeSet<>();
			for (Season s : bySeason.keySet()) {
				for (int m : s.months()) {
					if (!months.add(m)) {
						throw new Unreadable("계절 겹침");
					}
				}
			}
			if (months.size() != 12) {
				throw new Unreadable("계절이 12개월이 아님");
			}
		}
		List<Row> rows = new ArrayList<>();
		bySeason.forEach((season, group) -> {
			Entry general = null;
			Entry[] byDay = new Entry[8];
			for (Entry e : group) {
				if (e.days == null) {
					if (general != null) {
						throw new Unreadable("시각 둘");
					}
					general = e;
					continue;
				}
				for (int d : e.days.days()) {
					if (byDay[d] != null) {
						throw new Unreadable("요일 겹침");
					}
					byDay[d] = e;
				}
			}
			if (general != null && noRest) {
				throw new Unreadable("쉬는 날 없음");
			}
			for (int d = 1; d <= 7; d++) {
				if (closed.contains(d)) {
					// 요일 이름을 적어 연다고 했는데 쉬는 날이면 원문끼리 어긋난다. "평일"처럼 묶어 쓴 것은 쉬는 날이 뺀다
					if (byDay[d] != null && !byDay[d].days.broad()) {
						throw new Unreadable("휴무 요일에 운영시간");
					}
					continue;
				}
				Entry e = byDay[d] != null ? byDay[d] : general;
				if (e == null) {
					throw new Unreadable("요일이 빔");
				}
				rows.add(new Row(d,
						season == null ? null : "%02d-01".formatted(season.from()),
						season == null ? null : "%02d-%02d".formatted(season.to(), MONTH_DAYS[season.to() - 1]),
						e.open, e.close, e.lastEntry, e.breakStart, e.breakEnd));
			}
		});
		if (rows.isEmpty()) {
			throw new Unreadable("모두 휴무");
		}
		return rows;
	}

	/** 쉬는 날 원문 → 매주 쉬는 요일. 예외(공휴일·명절·격주 등)는 넣지 않는다 */
	static Set<Integer> closedDays(String restText) {
		Set<Integer> closed = new TreeSet<>();
		boolean informative = false;
		for (String line : SourceText.lines(SourceText.normalize(restText), false)) {
			if (SourceText.isNote(line)) {
				continue;
			}
			Matcher header = HEADER.matcher(line);
			if (header.matches()) {
				// "[휴관일]"·"[정기휴무]" 같은 머리말은 넘긴다. "[사무실]" 같은 시설 구분은 못 읽음
				if (!header.group(1).contains("휴")) {
					throw new Unreadable("쉬는 날");
				}
				continue;
			}
			String body = PARENS.matcher(PARENS.matcher(line).replaceAll(" ")).replaceAll(" ");
			body = REST_EXCEPT_DAYS.matcher(body).replaceAll(" ").strip();
			for (String segment : REST_SPLIT.split(body)) {
				if (!segment.isBlank()) {
					informative = true;
					restSegment(segment.strip(), closed, true);
				}
			}
		}
		if (!informative || closed.size() == 7) {
			throw new Unreadable("쉬는 날");
		}
		return closed;
	}

	/** 쉬는 날 한 토막. 매주 쉬는 요일이면 closed에 더하고, 예외면 넘기고, 못 읽으면 Unreadable */
	private static void restSegment(String segment, Set<Integer> closed, boolean splitAnd) {
		if (REST_IRREGULAR.matcher(segment).find()) {
			return;
		}
		Matcher weekly = REST_WEEKLY.matcher(segment);
		if (weekly.matches()) {
			Set<Integer> days = days(weekly.group(1));
			String remainder = weekly.group(2) == null ? "" : weekly.group(2).strip();
			boolean exceptionOnly = remainder.isEmpty()
					|| ((REST_EXCEPTION.matcher(remainder).find() || REST_CONDITION.matcher(remainder).find())
							&& !REST_UNREADABLE.matcher(remainder).find());
			if (days != null && !days.isEmpty() && exceptionOnly) {
				closed.addAll(days);
				return;
			}
		}
		if (REST_CONDITION.matcher(segment).find()) {
			return;
		}
		if (splitAnd && REST_AND.matcher(segment).find()) {
			for (String part : REST_AND.split(segment)) {
				restSegment(part.strip(), closed, false);
			}
			return;
		}
		if (REST_NONE.matcher(segment).matches()) {
			return;
		}
		if (REST_UNREADABLE.matcher(segment).find() || !REST_EXCEPTION.matcher(segment).find()) {
			throw new Unreadable("쉬는 날");
		}
	}

	/** 요일 표현 → 요일 번호(1 월 … 7 일). 공휴일만이면 빈 집합, 요일 표현이 아니면 null */
	static Set<Integer> days(String expr) {
		String s = expr.replaceAll("법정\\s*공휴일", "공휴일").replaceAll("\\s*~\\s*", "~").strip();
		Set<Integer> days = new TreeSet<>();
		boolean holiday = false;
		for (String token : DAY_TOKEN_SEP.split(s)) {
			switch (token) {
				case "", "매주" -> {
				}
				case "매일", "연중무휴" -> addDays(days, 1, 7);
				case "평일", "주중" -> addDays(days, 1, 5);
				case "주말" -> addDays(days, 6, 7);
				case "공휴일", "휴일" -> holiday = true;
				default -> {
					Matcher range = DAY_RANGE.matcher(token);
					Matcher one = DAY_ONE.matcher(token);
					if (range.matches()) {
						addDays(days, weekday(range.group(1)), weekday(range.group(2)));
					} else if (one.matches()) {
						days.add(weekday(one.group(1)));
					} else {
						return null;
					}
				}
			}
		}
		if (days.isEmpty()) {
			return holiday ? Set.of() : null;
		}
		return days;
	}

	/** from부터 to까지 (일요일을 넘어가면 월요일로 이어진다) */
	private static void addDays(Set<Integer> days, int from, int to) {
		for (int d = from; ; d = d % 7 + 1) {
			days.add(d);
			if (d == to) {
				return;
			}
		}
	}

	private static int weekday(String name) {
		return "월화수목금토일".indexOf(name) + 1;
	}

	private static Season season(Matcher m) {
		int from = Integer.parseInt(m.group(1));
		int to = Integer.parseInt(m.group(2));
		if (from < 1 || from > 12 || to < 1 || to > 12) {
			throw new Unreadable("계절");
		}
		return new Season(from, to);
	}

	/** close면 24~29시를 자정 넘김으로 읽는다 */
	private static LocalTime time(Matcher m, int group, boolean close) {
		int h = Integer.parseInt(m.group(group));
		int min = Integer.parseInt(m.group(group + 1));
		if (min > 59 || h > (close ? 29 : 23)) {
			throw new Unreadable("시각");
		}
		return LocalTime.of(h % 24, min);
	}

	private static LocalTime lastEntryTime(Matcher m) {
		return m.group(1) != null ? time(m, 1, true) : time(m, 3, true);
	}

	/** BREAK는 "준비시간 15:00~17:00"(그룹 1~4)과 "15:00~17:00 브레이크타임"(5~8) 두 꼴이다 */
	private static void setBreak(Entry entry, Matcher m) {
		int first = m.group(1) != null ? 1 : 5;
		entry.breakStart = time(m, first, false);
		entry.breakEnd = time(m, first + 2, true);
	}
}
