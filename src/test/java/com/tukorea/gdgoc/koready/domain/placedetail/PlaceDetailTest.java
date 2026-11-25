package com.tukorea.gdgoc.koready.domain.placedetail;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.security.JwtProvider;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 장소 상세. 장소는 테스트가 직접 넣고(롤백된다), "지금"은 2026-11-01(일) 10:00이다.
 * 시험 장소는 화요일만 쉬고, 일요일은 09:00~18:00(입장 마감 17:00, 휴게 12:00~13:00)에 연다.
 */
@IntegrationTest
class PlaceDetailTest {

	private static final String TRIP = """
			{"nationality": "JP", "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 2,
			 "stay": {"type": "AREA", "areaCode": "MYEONGDONG"}}
			""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Value("${jwt.secret}")
	String jwtSecret;

	long userId;
	long placeId;
	Map<String, Object> classes;

	@BeforeEach
	void seed() {
		userId = insertUser();
		classes = jdbc.queryForMap("""
				SELECT p.class1_code, c1.name_ko AS class1_ko, p.class2_code, p.class3_code,
				       c3.name_ko AS class3_ko
				FROM places p JOIN place_classes c1 ON c1.code = p.class1_code JOIN place_classes c3 ON c3.code = p.class3_code
				WHERE p.content_type_id <> 32 LIMIT 1
				""");
		placeId = jdbc.queryForObject("""
				INSERT INTO places (content_id, content_type_id, class1_code, class2_code, class3_code, name_ko, name_en,
				                    ldong_regn_cd, ldong_signgu_cd, addr1, addr2, location, summary_ko, summary_en,
				                    overview_ko, overview_en, image_url, thumbnail_url, image_license,
				                    hours_type, hours_text_ko, hours_text_en, rest_text_ko, fee_type, fee_text_ko,
				                    stay_minutes, stay_basis, signal_count, source_modified_at, synced_at, active)
				VALUES (?, 12, ?, ?, ?, '시험 궁궐', 'Test Palace', '11', '110', '서울 종로구 사직로 161', '경복궁',
				        ST_SetSRID(ST_MakePoint(126.977, 37.5796), 4326)::geography, '조선의 법궁', 'Main palace',
				        '국문 소개', 'English overview', 'https://img.example/a.jpg', 'https://img.example/a_s.jpg', 'Type3',
				        'STRUCTURED', '09:00~18:00', '9 am to 6 pm', '매주 화요일', 'PAID', '성인 3,000원',
				        90, 'CLASS', 0, now(), now(), true)
				RETURNING id
				""", Long.class, "PD" + (System.nanoTime() % 1_000_000_000_000L), classes.get("class1_code"),
				classes.get("class2_code"), classes.get("class3_code"));
		jdbc.update("""
				INSERT INTO place_opening_hours (place_id, day_of_week, open_time, close_time, last_entry_time,
				                                 break_start, break_end)
				VALUES (?, 7, '09:00', '18:00', '17:00', '12:00', '13:00')
				""", placeId);
		for (int day : new int[] {1, 3, 4, 5, 6}) {
			jdbc.update("INSERT INTO place_opening_hours (place_id, day_of_week, open_time, close_time) "
					+ "VALUES (?, ?, '09:00', '18:00')", placeId, day);
		}
		jdbc.update("INSERT INTO place_opening_hours (place_id, day_of_week, season_from, season_to, open_time, close_time) "
				+ "VALUES (?, 1, '03-01', '10-31', '09:00', '19:00')", placeId);
		jdbc.update("INSERT INTO place_fees (place_id, audience, label_ko, label_en, amount_krw) "
				+ "VALUES (?, 'ADULT', '성인', 'Adult', 3000)", placeId);
		jdbc.update("INSERT INTO place_fees (place_id, audience, label_ko, amount_krw) VALUES (?, 'CHILD', '어린이', 0)",
				placeId);
	}

	@Test
	void anonymousGetsEveryField() {
		MvcTestResult result = get(mvc.get().uri("/api/places/{placeId}", placeId).exchange());

		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{"placeId": %d, "name": "시험 궁궐", "nameKo": "시험 궁궐",
				 "category": {"code": "%s", "name": "%s"}, "classification": {"code": "%s", "name": "%s"},
				 "location": {"lat": 37.5796, "lng": 126.977},
				 "summary": "조선의 법궁",
				 "images": [{"url": "https://img.example/a.jpg", "thumbnailUrl": "https://img.example/a_s.jpg",
				             "license": "TYPE3"}],
				 "address": "서울 종로구 사직로 161 경복궁",
				 "hours": {"type": "STRUCTURED",
				           "now": {"status": "OPEN", "closesAt": "18:00", "lastEntryAt": "17:00"},
				           "closedDays": ["TUE"], "text": "09:00~18:00", "restText": "매주 화요일"},
				 "fees": {"type": "PAID", "text": "성인 3,000원",
				          "items": [{"audience": "ADULT", "label": "성인", "amount": {"amount": 3000, "currency": "KRW"}},
				                    {"audience": "CHILD", "label": "어린이", "amount": {"amount": 0, "currency": "KRW"}}]},
				 "overview": "국문 소개",
				 "inTrip": null, "favorited": false}
				""".formatted(placeId, classes.get("class1_code"), classes.get("class1_ko"), classes.get("class3_code"),
				classes.get("class3_ko")));
		assertThat(result).bodyJson().extractingPath("$.hours.weekly[*].dayOfWeek").asArray()
				.containsExactly("MON", "MON", "WED", "THU", "FRI", "SAT", "SUN");
		assertThat(result).bodyJson().extractingPath("$.hours.weekly[1].seasonFrom").isEqualTo("03-01");
		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{"hours": {"weekly": [
				  {"dayOfWeek": "MON", "openTime": "09:00", "closeTime": "18:00", "lastEntryTime": null,
				   "breakStart": null, "breakEnd": null, "seasonFrom": null, "seasonTo": null},
				  {"dayOfWeek": "MON", "openTime": "09:00", "closeTime": "19:00", "seasonFrom": "03-01", "seasonTo": "10-31"},
				  {"dayOfWeek": "WED"}, {"dayOfWeek": "THU"}, {"dayOfWeek": "FRI"}, {"dayOfWeek": "SAT"},
				  {"dayOfWeek": "SUN", "openTime": "09:00", "closeTime": "18:00", "lastEntryTime": "17:00",
				   "breakStart": "12:00", "breakEnd": "13:00", "seasonFrom": null, "seasonTo": null}]}}
				""");
	}

	@Test
	void favoritedAfterAddingFavorite() {
		assertThat(mvc.put().uri("/api/favorites/{placeId}", placeId).header("X-Dev-User-Id", userId))
				.hasStatus(HttpStatus.NO_CONTENT);

		assertThat(get(asUser(userId, "/api/places/{placeId}"))).bodyJson().extractingPath("$.favorited")
				.isEqualTo(true);
	}

	@Test
	void inTripIsFirstItemOfMyTrip() {
		long tripId = createTrip(userId);
		long day1 = insertDay(tripId, 1, "2026-11-10");
		long day2 = insertDay(tripId, 2, "2026-11-11");
		insertItem(day2, 1, placeId, "10:00");
		insertItem(day1, 2, placeId, "14:20");

		assertThat(get(asUser(userId, "/api/places/{placeId}?tripId=" + tripId))).bodyJson()
				.extractingPath("$.inTrip").isEqualTo(Map.of(
						"tripId", (int) tripId, "dayNumber", 1, "date", "2026-11-10", "arriveTime", "14:20"));
	}

	@Test
	void inTripIsNullForOthersMissingOrAnonymousTrip() {
		long otherUserId = insertUser();
		long othersTripId = createTrip(otherUserId);
		insertItem(insertDay(othersTripId, 1, "2026-11-10"), 1, placeId, "10:00");

		assertThat(get(asUser(userId, "/api/places/{placeId}?tripId=" + othersTripId))).bodyJson()
				.extractingPath("$.inTrip").isNull();
		assertThat(get(asUser(userId, "/api/places/{placeId}?tripId=" + (othersTripId + 1000)))).bodyJson()
				.extractingPath("$.inTrip").isNull();
		assertThat(get(mvc.get().uri("/api/places/{placeId}?tripId=" + othersTripId, placeId).exchange())).bodyJson()
				.extractingPath("$.inTrip").isNull();
	}

	@Test
	void englishFallsBackToKoreanWhenMissing() {
		// 분류 영문 이름은 아직 적재하지 않아 국문으로 나온다
		MvcTestResult result = get(mvc.get().uri("/api/places/{placeId}", placeId).header("Accept-Language", "en")
				.exchange());

		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{"name": "Test Palace", "nameKo": "시험 궁궐", "category": {"name": "%s"}, "summary": "Main palace",
				 "overview": "English overview",
				 "hours": {"text": "9 am to 6 pm", "restText": "매주 화요일"},
				 "fees": {"text": "성인 3,000원", "items": [{"label": "Adult"}, {"label": "어린이"}]}}
				""".formatted(classes.get("class1_ko")));
	}

	@Test
	void foreignCurrencyWithoutRateGivesKrw() {
		jdbc.update("DELETE FROM exchange_rates WHERE currency = 'JPY'");

		assertThat(get(mvc.get().uri("/api/places/{placeId}", placeId).header("X-Currency", "JPY").exchange()))
				.bodyJson().extractingPath("$.fees.items[0].amount")
				.isEqualTo(Map.of("amount", 3000, "currency", "KRW"));
	}

	@Test
	void foreignCurrencyWithRateIsConverted() {
		jdbc.update("DELETE FROM exchange_rates WHERE currency = 'JPY'");
		jdbc.update("""
				INSERT INTO exchange_rates (rate_date, currency, krw_per_unit, fetched_at)
				VALUES ('2026-10-30', 'JPY', 9.5000, now())
				""");

		// 3000 / 9.5 = 315.79 → 316엔 (정수)
		assertThat(get(mvc.get().uri("/api/places/{placeId}", placeId).header("X-Currency", "JPY").exchange()))
				.bodyJson().extractingPath("$.fees.items[0].amount")
				.isEqualTo(Map.of("amount", 316, "currency", "JPY"));
	}

	@Test
	void inactivePlaceWithoutPhotoIsStillShown() {
		jdbc.update("UPDATE places SET active = false, image_url = NULL, thumbnail_url = NULL WHERE id = ?", placeId);

		assertThat(get(mvc.get().uri("/api/places/{placeId}", placeId).exchange())).bodyJson()
				.extractingPath("$.images").asArray().isEmpty();
	}

	@Test
	void unstructuredHoursGiveNoWeeklyOrClosedDaysEvenWithStoredRows() {
		jdbc.update("UPDATE places SET hours_type = 'UNKNOWN' WHERE id = ?", placeId);

		assertThat(get(mvc.get().uri("/api/places/{placeId}", placeId).exchange())).bodyJson().isLenientlyEqualTo("""
				{"hours": {"type": "UNKNOWN", "now": {"status": "UNKNOWN"}, "weekly": [], "closedDays": []}}
				""");
	}

	@Test
	void missingPlaceIsPlaceNotFound() {
		assertThat(mvc.get().uri("/api/places/{placeId}", placeId + 1000))
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson().extractingPath("$.code").isEqualTo("PLACE_NOT_FOUND");
	}

	@Test
	void sentTokenIsChecked() {
		String expired = new JwtProvider(jwtSecret, 30, 14,
				Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)).createAccessToken(userId);

		assertThat(mvc.get().uri("/api/places/{placeId}", placeId).header("Authorization", "Bearer garbage"))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
		assertThat(mvc.get().uri("/api/places/{placeId}", placeId).header("Authorization", "Bearer " + expired))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("TOKEN_EXPIRED");
	}

	private MvcTestResult get(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		return result;
	}

	private MvcTestResult asUser(long asUserId, String uri) {
		return mvc.get().uri(uri, placeId).header("X-Dev-User-Id", asUserId).exchange();
	}

	private long insertUser() {
		return jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}

	private long createTrip(long ownerId) {
		assertThat(mvc.post().uri("/api/trips").header("X-Dev-User-Id", ownerId)
				.contentType(MediaType.APPLICATION_JSON).content(TRIP).exchange()).hasStatus(HttpStatus.CREATED);
		return jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, ownerId);
	}

	private long insertDay(long tripId, int dayNumber, String date) {
		return jdbc.queryForObject("INSERT INTO trip_days (trip_id, day_number, trip_date) VALUES (?, ?, ?::date) RETURNING id",
				Long.class, tripId, dayNumber, date);
	}

	private void insertItem(long dayId, int seq, long place, String arriveTime) {
		jdbc.update("""
				INSERT INTO trip_items (trip_day_id, seq, place_id, origin, arrive_time, depart_time, stay_minutes)
				VALUES (?, ?, ?, 'SELECTED', ?::time, ?::time, 60)
				""", dayId, seq, place, arriveTime, arriveTime);
	}
}
