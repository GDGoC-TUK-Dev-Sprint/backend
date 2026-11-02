package com.tukorea.gdgoc.koready.global.util;

import com.tukorea.gdgoc.koready.global.exception.FieldErrors;

/**
 * 이름·주소 검색어. 앞뒤 공백(전각 공백 포함)을 빼고 찾으며, 길이는 뺀 뒤의 글자 수(코드 포인트)로 잰다.
 */
public final class SearchText {

	private SearchText() {
	}

	/** 앞뒤 공백을 뺀 검색어. 공백만이거나 maxLength자를 넘으면 errors에 field로 넣는다 */
	public static String strip(String query, String field, int maxLength, FieldErrors errors) {
		return strip(query, field, 1, maxLength, errors);
	}

	/** 앞뒤 공백을 뺀 검색어. 공백만이거나 minLength자보다 짧거나 maxLength자를 넘으면 errors에 field로 넣는다 */
	public static String strip(String query, String field, int minLength, int maxLength, FieldErrors errors) {
		String text = query.strip();
		int length = text.codePointCount(0, text.length());
		if (text.isEmpty()) {
			errors.add(field, "공백만으로는 찾을 수 없습니다");
		} else if (length < minLength) {
			errors.add(field, minLength + "자 이상으로 찾을 수 있습니다");
		} else if (length > maxLength) {
			errors.add(field, maxLength + "자 이하로 찾을 수 있습니다");
		}
		return text;
	}

	/** ILIKE 부분 일치 패턴. 검색어의 와일드카드 문자(%, _)와 이스케이프 문자(\)는 글자 그대로 찾는다 */
	public static String containsPattern(String text) {
		return "%" + escape(text) + "%";
	}

	/** ILIKE 앞부분 일치 패턴. 와일드카드 문자는 {@link #containsPattern}처럼 글자 그대로 찾는다 */
	public static String prefixPattern(String text) {
		return escape(text) + "%";
	}

	private static String escape(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
