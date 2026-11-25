package com.tukorea.gdgoc.koready.global.util;

/**
 * 문자열을 DB 열 길이에 맞출 때 쓴다.
 */
public final class Texts {

	private Texts() {
	}

	/**
	 * 글자 수로 자른다. PostgreSQL varchar(n)이 글자(코드 포인트) 수로 세므로 UTF-16 두 칸인 글자(이모지 등)를 반으로 가르지 않는다.
	 * 여러 코드 포인트로 된 글자(가족 이모지·국기)는 중간에서 잘릴 수 있다. null이면 null
	 */
	public static String truncate(String value, int maxLength) {
		if (value == null || value.codePointCount(0, value.length()) <= maxLength) {
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, maxLength));
	}
}
