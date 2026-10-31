package com.tukorea.gdgoc.koready.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.common.LatLng;
import com.tukorea.gdgoc.koready.meta.Area;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 여행 만들기. "오늘"은 2026-11-01이다 ({@link com.tukorea.gdgoc.koready.support.TestClockConfig}).
 */
@IntegrationTest
class TripCreateTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	long userId;

	@BeforeEach
	void createUser() {
		userId = jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}

	@Test
	void createsDraftTripAtLodging() {
		Map<String, Object> lodging = jdbc.queryForMap("""
				SELECT id, name_ko, addr1, addr2, ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lng
				FROM places WHERE content_type_id = 32 AND addr2 <> '' ORDER BY id LIMIT 1
				""");
		long placeId = ((Number) lodging.get("id")).longValue();

		MvcTestResult result = create("""
				{"nationality": "JP", "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 2,
				 "budgetPerPerson": {"amount": 500000, "currency": "KRW"}, "categories": ["HS", "SH"],
				 "stay": {"type": "PLACE", "placeId": %d}}
				""".formatted(placeId));

		long tripId = jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, userId);
		var body = assertThat(result).bodyJson();
		body.isLenientlyEqualTo("""
				{"tripId": %d, "ticketNo": "TK-%04d", "status": "DRAFT", "editable": true, "progress": "UPCOMING",
				 "title": null, "nationality": {"code": "JP", "name": "일본"},
				 "startDate": "2026-11-10", "endDate": "2026-11-12", "nights": 2, "days": 3, "partySize": 2,
				 "budgetPerPerson": {"amount": 500000, "currency": "KRW"},
				 "stay": {"type": "PLACE", "placeId": %d, "areaCode": null},
				 "routeCreated": false, "confirmedAt": null}
				""".formatted(tripId, tripId, placeId));
		body.extractingPath("$").asMap().containsOnlyKeys("tripId", "ticketNo", "status", "editable", "progress",
				"title", "nationality", "startDate", "endDate", "nights", "days", "partySize", "budgetPerPerson",
				"categories", "stay", "routeCreated", "confirmedAt");
		body.extractingPath("$.categories").asArray()
				.containsExactly(Map.of("code", "SH", "name", "쇼핑"), Map.of("code", "HS", "name", "역사관광"));
		body.extractingPath("$.stay.name").isEqualTo(lodging.get("name_ko"));
		body.extractingPath("$.stay.nameKo").isEqualTo(lodging.get("name_ko"));
		body.extractingPath("$.stay.address").isEqualTo(lodging.get("addr1") + " " + lodging.get("addr2"));
		body.extractingPath("$.stay.location").isEqualTo(Map.of("lat", lodging.get("lat"), "lng", lodging.get("lng")));

		assertThat(jdbc.queryForMap("SELECT * FROM trips WHERE id = ?", tripId))
				.containsEntry("nationality_code", "JP")
				.containsEntry("party_size", 2)
				.containsEntry("budget_per_person_krw", 500000)
				.containsEntry("stay_type", "PLACE")
				.containsEntry("stay_place_id", placeId)
				.containsEntry("stay_area_code", null)
				.containsEntry("stay_name", lodging.get("name_ko"));
		assertThat(savedStayLocation(tripId)).isEqualTo(new LatLng((Double) lodging.get("lat"), (Double) lodging.get("lng")));
		assertThat(jdbc.queryForList("SELECT class1_code FROM trip_categories WHERE trip_id = ?", String.class, tripId))
				.containsExactlyInAnyOrder("HS", "SH");
	}

	@Test
	void areaCenterIsStayAndNamesFollowRequestLanguage() {
		MvcTestResult result = create("""
				{"nationality": "US", "startDate": "2026-11-01", "endDate": "2026-11-01", "partySize": 1,
				 "stay": {"type": "AREA", "areaCode": "MYEONGDONG"}}
				""", "en");

		var body = assertThat(result).bodyJson();
		body.isLenientlyEqualTo("""
				{"progress": "ONGOING", "nationality": {"code": "US", "name": "United States"},
				 "nights": 0, "days": 1, "partySize": 1, "budgetPerPerson": null, "categories": [],
				 "stay": {"type": "AREA", "placeId": null, "areaCode": "MYEONGDONG",
				          "name": "Myeongdong", "nameKo": "명동", "address": null}}
				""");
		body.extractingPath("$.stay.location").isEqualTo(
				Map.of("lat", Area.MYEONGDONG.getCenter().lat(), "lng", Area.MYEONGDONG.getCenter().lng()));
		assertThat(jdbc.queryForMap("SELECT * FROM trips WHERE user_id = ?", userId))
				.containsEntry("stay_type", "AREA")
				.containsEntry("stay_place_id", null)
				.containsEntry("stay_area_code", "MYEONGDONG")
				.containsEntry("stay_name", "명동")
				.containsEntry("stay_address", null);
		long tripId = jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, userId);
		assertThat(savedStayLocation(tripId)).isEqualTo(Area.MYEONGDONG.getCenter());
	}

	@Test
	void longLodgingAddressIsCutToColumnLength() {
		long placeId = jdbc.queryForObject(
				"SELECT id FROM places WHERE content_type_id = 32 AND active ORDER BY id LIMIT 1", Long.class);
		// addr1 + " " + addr2 = 301글자. 이모지는 UTF-16으로 두 칸이지만 PostgreSQL은 한 글자로 센다
		jdbc.update("UPDATE places SET addr1 = repeat('가', 100), addr2 = repeat('😀', 200) WHERE id = ?", placeId);

		MvcTestResult result = create(placeTrip(placeId));

		String saved = jdbc.queryForObject("SELECT stay_address FROM trips WHERE user_id = ?", String.class, userId);
		assertThat(saved).isEqualTo("가".repeat(100) + " " + "😀".repeat(199));
		assertThat(result).bodyJson().extractingPath("$.stay.address").isEqualTo(saved);
	}

	@Test
	void replacesDraftTripWithItsDays() {
		create(areaTrip("2026-11-10", "2026-11-12"));
		long oldTripId = jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, userId);
		jdbc.update("INSERT INTO trip_days (trip_id, day_number, trip_date) VALUES (?, 1, '2026-11-10')", oldTripId);

		create(areaTrip("2026-11-20", "2026-11-21"));

		assertThat(jdbc.queryForList("SELECT id FROM trips WHERE user_id = ?", Long.class, userId))
				.hasSize(1)
				.doesNotContain(oldTripId);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_days WHERE trip_id = ?", Long.class, oldTripId)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_categories WHERE trip_id = ?", Long.class, oldTripId))
				.isZero();
	}

	@Test
	void rejectsWhileConfirmedTripIsNotOver() {
		create(areaTrip("2026-11-01", "2026-11-01"));
		long confirmedId = jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, userId);
		jdbc.update("UPDATE trips SET status = 'CONFIRMED', confirmed_at = now() WHERE id = ?", confirmedId);

		assertThat(post(areaTrip("2026-11-10", "2026-11-12")))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "ACTIVE_TRIP_EXISTS", "fields": [], "activeTripId": %d}
						""".formatted(confirmedId));
		assertThat(jdbc.queryForList("SELECT id FROM trips WHERE user_id = ?", Long.class, userId))
				.containsExactly(confirmedId);
	}

	@Test
	void endedConfirmedTripDoesNotBlockAndIsKept() {
		create(areaTrip("2026-11-10", "2026-11-12"));
		jdbc.update("""
				UPDATE trips SET status = 'CONFIRMED', start_date = '2026-10-29', end_date = '2026-10-31' WHERE user_id = ?
				""", userId);

		create(areaTrip("2026-11-10", "2026-11-12"));

		assertThat(jdbc.queryForList("SELECT status FROM trips WHERE user_id = ? ORDER BY id", String.class, userId))
				.containsExactly("CONFIRMED", "DRAFT");
	}

	@Test
	void stayPlaceMustBeLodging() {
		long attractionId = jdbc.queryForObject(
				"SELECT id FROM places WHERE content_type_id <> 32 ORDER BY id LIMIT 1", Long.class);
		long missingId = jdbc.queryForObject("SELECT max(id) + 1 FROM places", Long.class);

		for (long placeId : new long[] {attractionId, missingId}) {
			assertThat(post(placeTrip(placeId)))
					.hasStatus(HttpStatus.NOT_FOUND)
					.bodyJson()
					.extractingPath("$.code").isEqualTo("PLACE_NOT_FOUND");
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM trips WHERE user_id = ?", Long.class, userId)).isZero();
	}

	@Test
	void requiredValuesAreMissing() {
		assertThat(post("""
				{"stay": {}}
				"""))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.fields[*].field").asArray()
				.containsExactlyInAnyOrder("nationality", "startDate", "endDate", "partySize", "stay.type");
		assertThat(post(areaTrip("2026-11-10", "2026-11-12").replace("[\"HS\"]", "[\"HS\", null]")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.fields[*].field").asArray()
				.containsExactly("categories[1]");
	}

	@Test
	void codesOutsideOptionsAreBadFormat() {
		assertThat(post(areaTrip("2026-11-10", "2026-11-12").replace("\"JP\"", "\"KR\"")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "nationality", "reason": "형식이 맞지 않습니다"}]}
						""");
		assertThat(post(areaTrip("2026-11-10", "2026-11-12").replace("[\"HS\"]", "[\"HS\", \"AC\"]")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "categories[1]"}]}
						""");
		assertThat(post(areaTrip("2026-11-10", "2026-11-12").replace("MYEONGDONG", "SEOUL")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "stay.areaCode"}]}
						""");
	}

	@Test
	void conditionsAreCheckedTogether() {
		assertThat(post("""
				{"nationality": "JP", "startDate": "2026-10-31", "endDate": "2026-10-30", "partySize": 11,
				 "budgetPerPerson": {"amount": 300, "currency": "USD"}, "stay": {"type": "PLACE"}}
				"""))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [
						  {"field": "startDate", "reason": "오늘(서울) 이후여야 합니다"},
						  {"field": "endDate", "reason": "시작일과 같거나 뒤여야 합니다"},
						  {"field": "partySize", "reason": "1~10명이어야 합니다"},
						  {"field": "budgetPerPerson.currency", "reason": "지금은 KRW만 받습니다"},
						  {"field": "stay.placeId", "reason": "PLACE이면 필수입니다"}]}
						""");
	}

	@Test
	void tripIsAtMostTenDays() {
		create(areaTrip("2026-11-10", "2026-11-19"));

		assertThat(post(areaTrip("2026-11-10", "2026-11-20")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "endDate", "reason": "여행은 최대 10일입니다"}]}
						""");
	}

	@Test
	void stayValuesDependOnType() {
		assertThat(post(areaTrip("2026-11-10", "2026-11-12").replace(", \"areaCode\": \"MYEONGDONG\"", "")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"fields": [{"field": "stay.areaCode", "reason": "AREA이면 필수입니다"}]}
						""");
		assertThat(post(areaTrip("2026-11-10", "2026-11-12").replace("\"AREA\"", "\"PIN\"")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"fields": [{"field": "stay.type"}]}
						""");
	}

	@Test
	void budgetIsWholeWon() {
		assertThat(post(areaTrip("2026-11-10", "2026-11-12")
				.replace("\"partySize\": 2", "\"partySize\": 0, \"budgetPerPerson\": {\"amount\": 1000.5, \"currency\": \"KRW\"}")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"fields": [{"field": "partySize"}, {"field": "budgetPerPerson.amount", "reason": "원 단위 정수여야 합니다"}]}
						""");
	}

	@Test
	void partySizeIsWholeNumber() {
		assertThat(post(areaTrip("2026-11-10", "2026-11-12").replace("\"partySize\": 2", "\"partySize\": 2.7")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "partySize", "reason": "형식이 맞지 않습니다"}]}
						""");
	}

	@Test
	void requiresExistingUser() {
		String body = areaTrip("2026-11-10", "2026-11-12");
		long missingUserId = jdbc.queryForObject("SELECT max(id) + 1 FROM users", Long.class);

		assertThat(mvc.post().uri("/api/trips").contentType(MediaType.APPLICATION_JSON).content(body))
				.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/trips").header("X-Dev-User-Id", missingUserId)
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("UNAUTHORIZED");
	}

	private MvcTestResult post(String body) {
		return post(body, "ko");
	}

	private MvcTestResult post(String body, String language) {
		return mvc.post().uri("/api/trips")
				.header("X-Dev-User-Id", userId)
				.header("Accept-Language", language)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.exchange();
	}

	private MvcTestResult create(String body) {
		return create(body, "ko");
	}

	/** 여행을 만든다. 테스트의 요청들은 한 트랜잭션에서 돌므로, 요청이 끝난 것처럼 DB에 쓰고 영속성 컨텍스트를 비운다 */
	private MvcTestResult create(String body, String language) {
		MvcTestResult result = post(body, language);
		assertThat(result).hasStatus(HttpStatus.CREATED);
		entityManager.flush();
		entityManager.clear();
		return result;
	}

	/** 저장된 숙소 좌표. 응답은 저장 전 메모리의 좌표를 그대로 주므로 DB 값을 따로 본다 */
	private LatLng savedStayLocation(long tripId) {
		return jdbc.queryForObject(
				"SELECT ST_Y(stay_location::geometry), ST_X(stay_location::geometry) FROM trips WHERE id = ?",
				(rs, i) -> new LatLng(rs.getDouble(1), rs.getDouble(2)), tripId);
	}

	private static String areaTrip(String startDate, String endDate) {
		return """
				{"nationality": "JP", "startDate": "%s", "endDate": "%s", "partySize": 2, "categories": ["HS"],
				 "stay": {"type": "AREA", "areaCode": "MYEONGDONG"}}
				""".formatted(startDate, endDate);
	}

	private static String placeTrip(long placeId) {
		return """
				{"nationality": "JP", "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 2,
				 "stay": {"type": "PLACE", "placeId": %d}}
				""".formatted(placeId);
	}
}
