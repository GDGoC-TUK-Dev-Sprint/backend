package com.tukorea.gdgoc.koready.loader;

import com.tukorea.gdgoc.koready.place.FeeAudience;
import com.tukorea.gdgoc.koready.place.FeeType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 요금 원문을 대상별 금액으로 나눈다 ({@code place_fees}).
 * 경비는 {@code PAID}인 장소의 성인({@code ADULT}) 금액을 쓰므로, 성인 금액이 하나로 정해지지 않으면
 * (평일·주말, 좌석 등급, 시설별 구역) {@code VARIES}로 둔다. 못 읽으면 {@code UNKNOWN}.
 */
final class FeeParser {

	record Fee(FeeAudience audience, String labelKo, int amountKrw) {
	}

	/** reason: UNKNOWN·VARIES인 이유 (적재 로그용) */
	record Result(FeeType type, List<Fee> fees, String reason) {

		static Result of(FeeType type, String reason) {
			return new Result(type, List.of(), reason);
		}
	}

	/** 관광지 반복정보 "입장료"처럼 대상 표기가 없는 금액의 이름 */
	private static final String DEFAULT_LABEL = "입장료";
	private static final int LABEL_MAX = 100;

	private static final Pattern HEADER = Pattern.compile("^\\[(.+)]$");
	private static final Pattern AMOUNT = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원(\\s*\\([^()]*\\))?");
	private static final Pattern MAN_WON = Pattern.compile("(\\d+)\\s*만\\s*원");
	private static final Pattern PARENS = Pattern.compile("\\([^()]*\\)");
	private static final Pattern FREE_ONLY = Pattern.compile(
			"^(?:(?:입장료|입장|관람료|관람|이용료|이용|요금|참가비|참가)\\s*)?(?:무료(?:\\s*(?:입장|개방|관람|이용|운영|참가|참여))?|없음)$");
	/** "미취학 아동 무료", "상설전시 무료입장" */
	private static final Pattern LABELED_FREE = Pattern.compile("^(.+?)\\s*:?\\s*무료(?:\\s*(?:입장|개방|관람|이용))?$");
	/** "무료 : 미취학 아동"처럼 무료 대상만 늘어놓은 줄 */
	private static final Pattern FREE_FOR = Pattern.compile("^무료\\s*[:：]");
	private static final Pattern VARIES = Pattern.compile("상이|다름|다르|별도|변동|따라|마다|별로|별\\s|시즌|회차|좌석|객실|종목|부분");
	/** "유료 (1인 30,000원)" */
	private static final Pattern PAID_IN_PARENS = Pattern.compile("^유료\\s*\\(\\s*(1인)?\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원\\s*\\)$");
	/** "청소년(13세~18세) 6,000"처럼 "원"이 빠진 금액 */
	private static final Pattern BARE_AMOUNT = Pattern.compile("^(?=.*[가-힣)]).+\\s\\d{1,3}(?:,\\d{3})+$");
	/** "※ 무료" 다음 줄들은 무료 대상 목록이다 */
	private static final Pattern FREE_LIST_NOTE = Pattern.compile("^※\\s*무료\\s*(?:[:：]\\s*)?(?:\\([^()]*\\))?\\s*$");
	/** "[무료 관람의 날]"·"[할인]" 구역은 기본 요금이 아니다 */
	private static final Pattern INFO_HEADER = Pattern.compile("무료|할인|혜택");
	private static final Pattern DISCOUNT = Pattern.compile("\\d+\\s*%|할인|혜택");
	private static final Pattern PAID_WORD = Pattern.compile("유료");
	private static final Pattern FREE_WORD = Pattern.compile("무료");
	private static final Pattern INFO_ONLY = Pattern.compile("참조|참고|문의|확인|홈페이지|예약");
	private static final Pattern SEAT = Pattern.compile("(?i)(?:VIP|\\b[A-Z]{1,3})\\s*석|석\\s*$");
	private static final Pattern DAY_LABEL = Pattern.compile("평일|주말|공휴일|휴일|요일|주중|주간|야간|오전|오후|이후|이전|\\d{1,2}:\\d{2}");
	private static final Pattern LABEL_TRIM = Pattern.compile("^[\\s,/·:\\-]+|[\\s,/·:\\-]+$");

	private static final Pattern GROUP = Pattern.compile("단체");
	private static final Pattern SENIOR_WORD = Pattern.compile("경로|노인|어르신|시니어");
	private static final Pattern ADULT_WORD = Pattern.compile("성인|어른|대인");
	/** 대상 없이 쓰는 기본 요금 표기. 어린이·청소년 표기가 함께 있으면 그쪽을 따른다 ("개인 소인") */
	private static final Pattern GENERAL_WORD = Pattern.compile("일반|개인|1인|입장료|관람료|기본");
	private static final Pattern OTHER_WORD = Pattern.compile(
			"대학생|우대|할인|장애|유공|군인|외국인|내국인|주민|구민|시민|가족|연간|회원|얼리버드|통합|패키지|동반|보호자|임산부|다자녀");
	private static final Pattern CHILD_WORD = Pattern.compile("어린이|소인|아동|초등|유아|미취학|영유아|개월|소아|키즈");
	private static final Pattern YOUTH_WORD = Pattern.compile("청소년|중고생|중·고|중학생|고등학생|학생");
	private static final Pattern AGE_RANGE = Pattern.compile("만?\\s*(\\d+)\\s*세?\\s*~\\s*만?\\s*(\\d+)\\s*세");
	private static final Pattern AGE_FROM = Pattern.compile("만?\\s*(\\d+)\\s*세\\s*이상");
	private static final Pattern AGE_UNTIL = Pattern.compile("만?\\s*(\\d+)\\s*세\\s*(?:이하|미만)");

	private FeeParser() {
	}

	static Result parse(String text) {
		String normalized = SourceText.normalize(text);
		if (normalized.isEmpty()) {
			return Result.of(FeeType.UNKNOWN, "요금 없음");
		}
		normalized = MAN_WON.matcher(normalized).replaceAll(m -> Integer.parseInt(m.group(1)) * 10000 + "원");

		List<Fee> fees = new ArrayList<>();
		String header = null;
		int facilityHeaders = 0;
		boolean dayHeaders = false;
		boolean free = false;
		boolean varies = false;
		boolean paidWord = false;
		boolean noteVaries = false;
		boolean infoSection = false;
		boolean freeList = false;
		for (String raw : SourceText.lines(normalized, false)) {
			String line = BARE_AMOUNT.matcher(raw).matches() ? raw + "원" : raw;
			if (SourceText.isNote(line)) {
				noteVaries |= VARIES.matcher(line).find();
				freeList = FREE_LIST_NOTE.matcher(line).matches();
				continue;
			}
			Matcher h = HEADER.matcher(line);
			if (h.matches()) {
				header = h.group(1).strip();
				freeList = false;
				infoSection = INFO_HEADER.matcher(header).find();
				if (infoSection) {
					continue;
				}
				if (DAY_LABEL.matcher(header).find()) {
					dayHeaders = true;
				} else if (byLabel(header) == null && !GROUP.matcher(header).find()) {
					facilityHeaders++;
				}
				continue;
			}
			boolean hasAmount = AMOUNT.matcher(line).find();
			if (infoSection || (freeList && !hasAmount)) {
				continue;
			}
			freeList = false;
			String main = PARENS.matcher(line).replaceAll(" ").strip();
			Matcher paidInParens = PAID_IN_PARENS.matcher(line);
			if (paidInParens.matches()) {
				String label = label(paidInParens.group(1) == null ? "" : paidInParens.group(1), header);
				fees.add(new Fee(audience(label, header), label, Integer.parseInt(paidInParens.group(2).replace(",", ""))));
				continue;
			}
			if (hasAmount) {
				if (!amounts(line, header, fees)) {
					return Result.of(FeeType.UNKNOWN, "금액 문장");
				}
				continue;
			}
			if (FREE_ONLY.matcher(main).matches()) {
				free = true;
				continue;
			}
			if (FREE_FOR.matcher(main).find()) {
				continue;
			}
			boolean hasPaid = PAID_WORD.matcher(main).find();
			if (hasPaid && FREE_WORD.matcher(main).find()) {
				varies = true;
				continue;
			}
			Matcher labeledFree = LABELED_FREE.matcher(main);
			if (labeledFree.matches() && !hasPaid && !VARIES.matcher(main).find()) {
				String label = label(labeledFree.group(1), header);
				fees.add(new Fee(audience(label, header), label, 0));
				free = true;
				continue;
			}
			if (VARIES.matcher(line).find()) {
				varies = true;
				continue;
			}
			if (hasPaid) {
				paidWord = true;
				continue;
			}
			if (INFO_ONLY.matcher(main).find() || DISCOUNT.matcher(main).find()) {
				continue;
			}
			return Result.of(FeeType.UNKNOWN, "문장");
		}

		if (fees.stream().anyMatch(f -> f.amountKrw() > 0)) {
			if (dayHeaders || fees.stream().anyMatch(f -> DAY_LABEL.matcher(f.labelKo()).find())) {
				return Result.of(FeeType.VARIES, "요일·시간대별 요금");
			}
			if (facilityHeaders > 1) {
				return Result.of(FeeType.VARIES, "시설별 요금");
			}
			if (fees.stream().anyMatch(f -> SEAT.matcher(f.labelKo()).find())) {
				return Result.of(FeeType.VARIES, "좌석별 요금");
			}
			long adultAmounts = fees.stream().filter(f -> f.audience() == FeeAudience.ADULT)
					.map(Fee::amountKrw).distinct().count();
			if (adultAmounts > 1) {
				return Result.of(FeeType.VARIES, "성인 요금 여럿");
			}
			// 강좌·체험·예매가처럼 정해진 성인 입장료가 없으면 경비에 쓸 금액이 없다
			if (adultAmounts == 0) {
				return Result.of(FeeType.VARIES, "성인 요금 없음");
			}
			return new Result(FeeType.PAID, List.copyOf(new LinkedHashSet<>(fees)), null);
		}
		if (varies) {
			return Result.of(FeeType.VARIES, "상이");
		}
		if (free) {
			return Result.of(FeeType.FREE, null);
		}
		if (noteVaries) {
			return Result.of(FeeType.VARIES, "상이 (안내)");
		}
		return Result.of(FeeType.UNKNOWN, paidWord ? "유료인데 금액 없음" : "정보 없음");
	}

	/** 한 줄의 "대상 금액원" 묶음들. 괄호 안 금액·금액 뒤 문장이 있으면 false */
	private static boolean amounts(String line, String header, List<Fee> fees) {
		Matcher a = AMOUNT.matcher(line);
		int prev = 0;
		while (a.find()) {
			if (insideParens(line, a.start())) {
				return false;
			}
			String label = label(line.substring(prev, a.start()), header);
			String suffix = a.group(2) == null ? "" : " " + a.group(2).strip();
			int amount = Integer.parseInt(a.group(1).replace(",", ""));
			for (String part : splitOutsideParens(label)) {
				String partLabel = truncate(part + suffix);
				fees.add(new Fee(audience(part, header), partLabel, amount));
			}
			prev = a.end();
		}
		return LABEL_TRIM.matcher(line.substring(prev)).replaceAll("").isEmpty();
	}

	/** 금액 앞 대상 표기. 비면 머리말이나 "입장료", 머리말이 있으면 앞에 붙인다 */
	private static String label(String raw, String header) {
		String label = LABEL_TRIM.matcher(raw).replaceAll("");
		if (label.isEmpty()) {
			return header != null ? header : DEFAULT_LABEL;
		}
		return header != null ? header + " " + label : label;
	}

	static FeeAudience audience(String label, String header) {
		if (header != null && GROUP.matcher(header).find()) {
			return FeeAudience.OTHER;
		}
		String own = header != null && label.startsWith(header + " ") ? label.substring(header.length() + 1) : label;
		FeeAudience audience = byLabel(own);
		if (audience == null && header != null) {
			audience = byLabel(header);
		}
		return audience == null ? FeeAudience.OTHER : audience;
	}

	/** 대상 표기 → 대상. 정할 수 없으면 null */
	private static FeeAudience byLabel(String s) {
		if (GROUP.matcher(s).find()) {
			return FeeAudience.OTHER;
		}
		if (SENIOR_WORD.matcher(s).find()) {
			return FeeAudience.SENIOR;
		}
		if (ADULT_WORD.matcher(s).find()) {
			return FeeAudience.ADULT;
		}
		if (OTHER_WORD.matcher(s).find()) {
			return FeeAudience.OTHER;
		}
		if (CHILD_WORD.matcher(s).find()) {
			return FeeAudience.CHILD;
		}
		if (YOUTH_WORD.matcher(s).find()) {
			return FeeAudience.YOUTH;
		}
		Matcher range = AGE_RANGE.matcher(s);
		if (range.find()) {
			int from = Integer.parseInt(range.group(1));
			int to = Integer.parseInt(range.group(2));
			if (to <= 12) {
				return FeeAudience.CHILD;
			}
			if (from >= 65) {
				return FeeAudience.SENIOR;
			}
			if (from >= 19) {
				return FeeAudience.ADULT;
			}
			return to <= 19 ? FeeAudience.YOUTH : null;
		}
		Matcher ageFrom = AGE_FROM.matcher(s);
		if (ageFrom.find()) {
			int from = Integer.parseInt(ageFrom.group(1));
			return from >= 65 ? FeeAudience.SENIOR : from >= 19 ? FeeAudience.ADULT : null;
		}
		Matcher ageUntil = AGE_UNTIL.matcher(s);
		if (ageUntil.find() && Integer.parseInt(ageUntil.group(1)) <= 13) {
			return FeeAudience.CHILD;
		}
		return GENERAL_WORD.matcher(s).find() ? FeeAudience.ADULT : null;
	}

	private static boolean insideParens(String s, int index) {
		int depth = 0;
		for (int i = 0; i < index; i++) {
			char c = s.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
			}
		}
		return depth > 0;
	}

	/** "청소년 / 65세 이상", "청소년, 대학생" → 둘. 괄호 안의 "/"·","는 나누지 않는다 */
	private static List<String> splitOutsideParens(String label) {
		Set<String> parts = new LinkedHashSet<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < label.length(); i++) {
			char c = label.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
			} else if ((c == '/' || c == ',') && depth == 0) {
				parts.add(label.substring(start, i).strip());
				start = i + 1;
			}
		}
		parts.add(label.substring(start).strip());
		parts.remove("");
		return List.copyOf(parts);
	}

	private static String truncate(String s) {
		return s.length() <= LABEL_MAX ? s : s.substring(0, LABEL_MAX);
	}
}
