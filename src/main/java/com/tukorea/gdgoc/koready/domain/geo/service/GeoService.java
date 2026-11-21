package com.tukorea.gdgoc.koready.domain.geo.service;

import com.tukorea.gdgoc.koready.domain.geo.dto.GeoReverseResponse;
import com.tukorea.gdgoc.koready.domain.geo.dto.GeoSearchItem;
import com.tukorea.gdgoc.koready.domain.geo.dto.GeoSearchResponse;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.exception.FieldErrors;
import com.tukorea.gdgoc.koready.global.util.SearchText;
import com.tukorea.gdgoc.koready.global.web.Language;
import com.tukorea.gdgoc.koready.infra.naver.GeocodeAddress;
import com.tukorea.gdgoc.koready.infra.naver.LocalSearchPlace;
import com.tukorea.gdgoc.koready.infra.naver.NaverMapsClient;
import com.tukorea.gdgoc.koready.infra.naver.NaverSearchClient;
import com.tukorea.gdgoc.koready.infra.naver.ReverseGeocodeResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 지도에서 숙소 위치를 고를 때의 주소·장소명 검색과 핀 위치 주소.
 * 네이버를 기다리는 동안 DB 연결을 잡지 않도록 트랜잭션 없이 부른다.
 */
@Service
@RequiredArgsConstructor
public class GeoService {

	private static final int MIN_QUERY_LENGTH = 2;
	private static final int MAX_QUERY_LENGTH = 100;
	/** 주소 결과는 최대 5개. 장소 결과는 네이버가 한 번에 주는 최대 수(5개)다 */
	private static final int MAX_ITEMS = 5;
	/** 서울 주소를 앞으로 올리려고 네이버에서 5개보다 넉넉히 받는다 (호출은 한 번) */
	private static final int ADDRESS_CANDIDATES = 20;

	/** 핀 위치는 법정동 시도 코드로 서울인지 본다 */
	private static final String SEOUL_REGION_CODE = "11";
	/** Geocoding·지역 검색은 법정동 코드를 주지 않아 주소의 시도 이름으로 본다 */
	private static final String SEOUL_SIDO = "서울특별시";

	private final NaverMapsClient naverMaps;
	private final NaverSearchClient naverSearch;

	/**
	 * 네이버 Geocoding 주소 결과를 서울 주소 먼저(그 안은 네이버 순서) 최대 5개, 이어서 네이버 지역 검색 장소 결과를 최대 5개 준다.
	 * 장소 결과는 네이버 이용 조건대로 순서·이름을 바꾸지 않고(영어로 요청해도 국문) 원문 링크와 함께 준다.
	 * 둘 중 하나라도 실패하면 {@code 502 EXTERNAL_API_FAILED}다
	 */
	public GeoSearchResponse search(String query, Language language) {
		FieldErrors errors = new FieldErrors();
		String text = SearchText.strip(query, "query", MIN_QUERY_LENGTH, MAX_QUERY_LENGTH, errors);
		errors.throwIfAny();
		List<GeoSearchItem> items = new ArrayList<>();
		naverMaps.geocode(text, ADDRESS_CANDIDATES).stream()
				.filter(address -> address.roadAddress() != null || address.jibunAddress() != null)
				.sorted(Comparator.comparing(address -> !SEOUL_SIDO.equals(address.sido())))
				.limit(MAX_ITEMS)
				.map(address -> addressItem(address, language))
				.forEach(items::add);
		naverSearch.searchLocal(text, NaverSearchClient.MAX_DISPLAY).stream()
				.map(GeoService::placeItem)
				.forEach(items::add);
		return new GeoSearchResponse(items);
	}

	/** 핀 좌표의 주소(도로명, 없으면 지번)와 서울 지원 지역인지 */
	public GeoReverseResponse reverse(double lat, double lng) {
		ReverseGeocodeResult result = naverMaps.reverseGeocode(lat, lng);
		String address = result.roadAddress() != null ? result.roadAddress() : result.jibunAddress();
		boolean inSeoul = result.legalCode() != null && result.legalCode().startsWith(SEOUL_REGION_CODE);
		return new GeoReverseResponse(address, inSeoul);
	}

	private static GeoSearchItem addressItem(GeocodeAddress address, Language language) {
		String korean = address.roadAddress() != null ? address.roadAddress() : address.jibunAddress();
		String text = language.pick(korean, address.englishAddress());
		return new GeoSearchItem(GeoSearchItem.Type.ADDRESS, text, text, new LatLng(address.lat(), address.lng()),
				SEOUL_SIDO.equals(address.sido()), null);
	}

	/** 주소는 도로명, 없으면 지번이다. 서울인지는 그 주소가 "서울특별시"로 시작하는지로 본다 */
	private static GeoSearchItem placeItem(LocalSearchPlace place) {
		String address = place.roadAddress() != null ? place.roadAddress() : place.jibunAddress();
		return new GeoSearchItem(GeoSearchItem.Type.PLACE, place.name(), address, new LatLng(place.lat(), place.lng()),
				address != null && address.startsWith(SEOUL_SIDO + " "), place.link());
	}
}
