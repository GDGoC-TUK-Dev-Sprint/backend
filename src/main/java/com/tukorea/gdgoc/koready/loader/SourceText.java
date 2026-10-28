package com.tukorea.gdgoc.koready.loader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TourAPI 원문(운영시간·쉬는 날·요금·개요) 다듬기와 줄 나누기.
 */
final class SourceText {

	/** {@code <br>} 태그. 뒤에 붙은 줄바꿈까지 하나로 본다 */
	private static final Pattern BR = Pattern.compile("(?i)<br\\s*/?>[ \\t]*\\r?\\n?");

	/** "10:00 - 22:00", "10:00–22:00" 같은 시각 범위 */
	private static final Pattern TIME_DASH =
			Pattern.compile("(\\d{1,2}:\\d{2})\\s*[-–—~]\\s*((?:익일|다음\\s*날|새벽)?\\s*\\d{1,2}:\\d{2})");

	/** "10시 30분", "18시" */
	private static final Pattern HOUR_MINUTE = Pattern.compile("(?<![:\\d])(\\d{1,2})\\s*시\\s*(\\d{1,2})\\s*분");
	private static final Pattern HOUR_ONLY = Pattern.compile("(?<![:\\d])(\\d{1,2})\\s*시(?![간작])");

	/** 줄 가운데의 "- " 글머리 ("17:20)- 주말 09:00~17:30") */
	private static final Pattern BULLET = Pattern.compile("(?<=\\S)\\s*-\\s+(?=\\S)");

	/** 줄 처음의 글머리 기호 */
	private static final Pattern LEADING_BULLET = Pattern.compile("^[-·•]\\s*");

	private static final Pattern SPACED_SLASH = Pattern.compile("\\s+/\\s+");

	private SourceText() {
	}

	/** DB에 넣는 원문: {@code <br>}을 줄바꿈으로 바꾸고 앞뒤 공백을 지운다. 비면 null */
	static String clean(String text) {
		if (text == null) {
			return null;
		}
		String cleaned = BR.matcher(text).replaceAll("\n").strip();
		return cleaned.isEmpty() ? null : cleaned;
	}

	/** 파싱 전에 물결표·쌍점·시각 표기를 하나로 맞춘다 */
	static String normalize(String text) {
		String s = clean(text);
		if (s == null) {
			return "";
		}
		s = s.replace('∼', '~').replace('～', '~').replace('〜', '~').replace('：', ':');
		s = HOUR_MINUTE.matcher(s).replaceAll("$1:$2");
		s = HOUR_ONLY.matcher(s).replaceAll("$1:00");
		return TIME_DASH.matcher(s).replaceAll("$1~$2");
	}

	/**
	 * 다듬은 원문을 뜻 단위 줄로 나눈다. 대괄호 머리말은 한 줄, "※"부터 그 줄 끝까지는 안내 한 줄로 둔다.
	 * {@code splitSlash}면 " / "로도 나눈다 (운영시간). 요금은 "청소년 / 65세 이상"처럼 대상 안에 있어 나누지 않는다
	 */
	static List<String> lines(String normalized, boolean splitSlash) {
		List<String> lines = new ArrayList<>();
		for (String raw : normalized.split("\n")) {
			String main = raw;
			String note = null;
			int mark = raw.indexOf('※');
			if (mark >= 0) {
				main = raw.substring(0, mark);
				note = raw.substring(mark).strip();
			}
			String t = main.strip();
			if (t.startsWith("*")) {
				lines.add(t);
				continue;
			}
			t = t.replace("[", "\n[").replace("]", "]\n");
			t = BULLET.matcher(t).replaceAll("\n");
			if (splitSlash) {
				t = slashOutsideParens(t);
			}
			for (String part : t.split("\n")) {
				String line = LEADING_BULLET.matcher(part.strip()).replaceFirst("").strip();
				if (!line.isEmpty()) {
					lines.add(line);
				}
			}
			if (note != null && note.length() > 1) {
				lines.add(note);
			}
		}
		return lines;
	}

	/** "※"나 "*"로 시작하는 안내 줄 */
	static boolean isNote(String line) {
		return line.startsWith("※") || line.startsWith("*");
	}

	/** 괄호 밖의 " / "만 줄바꿈으로 바꾼다 ("(준비시간 15:00~17:00 / 마지막 주문 20:00)"는 그대로) */
	private static String slashOutsideParens(String s) {
		StringBuilder out = new StringBuilder();
		int depth = 0;
		int last = 0;
		Matcher m = SPACED_SLASH.matcher(s);
		while (m.find()) {
			for (int i = last; i < m.start(); i++) {
				char c = s.charAt(i);
				depth += c == '(' ? 1 : c == ')' ? -1 : 0;
			}
			out.append(s, last, m.start()).append(depth > 0 ? m.group() : "\n");
			last = m.end();
		}
		return out.append(s.substring(last)).toString();
	}
}
