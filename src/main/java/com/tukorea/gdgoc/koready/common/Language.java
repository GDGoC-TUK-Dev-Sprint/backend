package com.tukorea.gdgoc.koready.common;

import java.util.List;
import java.util.Locale;

/**
 * 요청 언어. {@code Accept-Language}에서 고르고 없거나 지원하지 않으면 한국어다. 컨트롤러 파라미터 {@code Language language}로 받는다.
 */
public enum Language {

	KO, EN;

	private static final List<String> SUPPORTED = List.of("ko", "en");

	public static Language from(String acceptLanguage) {
		if (acceptLanguage == null || acceptLanguage.isBlank()) {
			return KO;
		}
		try {
			String tag = Locale.lookupTag(Locale.LanguageRange.parse(acceptLanguage), SUPPORTED);
			return "en".equals(tag) ? EN : KO;
		} catch (IllegalArgumentException e) {
			return KO;
		}
	}

	/** 표시 텍스트를 요청 언어로 고른다. 영문이 없으면 국문 */
	public String pick(String ko, String en) {
		return this == EN && en != null && !en.isBlank() ? en : ko;
	}
}
