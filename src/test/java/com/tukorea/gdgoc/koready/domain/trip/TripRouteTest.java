package com.tukorea.gdgoc.koready.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.tukorea.gdgoc.koready.domain.place.repository.PlaceSql;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 동선 만들기. 여행은 2026-11-10(화)~11-12(목) 명동 숙소이고, 장소는 테스트가 숙소 근처에 넣는다 (모두 롤백).
 * A·D는 걸어서 가는 곳, B는 3.3km(대중교통), C는 10:00~18:00에만 연다.
 * 담은 장소가 9곳(3일 × 하루 권장 최소 3곳)보다 적으면 덤프 장소가 추천순으로 채워진다 ({@code AUTO_FILL}).
 */
@IntegrationTest
class TripRouteTest {

	private static final String TRIP = """
			{"nationality": "JP", "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 2,
			 "stay": {"type": "AREA", "areaCode": "MYEONGDONG"}}
			""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	long userId;
	long tripId;
	double stayLat;
	double stayLng;
	long placeA;
	long placeB;
	long placeC;
	long placeD;

	@BeforeEach
	void seed() {
		userId = insertUser();
		tripId = createTrip(userId, TRIP);
		Map<String, Object> stay = jdbc.queryForMap(
				"SELECT ST_Y(stay_location::geometry) AS lat, ST_X(stay_location::geometry) AS lng FROM trips WHERE id = ?",
				tripId);
		stayLat = ((Number) stay.get("lat")).doubleValue();
		stayLng = ((Number) stay.get("lng")).doubleValue();
		// 위도 0.002 ≈ 220m(도보), 0.03 ≈ 3.3km(대중교통)
		placeA = insertPlace("TRA", "시험 장소 A", 0.002, "UNKNOWN", 60);
		placeB = insertPlace("TRB", "시험 장소 B", 0.03, "UNKNOWN", 30);
		placeC = insertPlace("TRC", "시험 장소 C", 0.003, "STRUCTURED", 30);
		insertHoursEveryDay(placeC, "10:00", "18:00");
		placeD = insertPlace("TRD", "시험 장소 D", -0.002, "ALWAYS_OPEN", 60);
		flush();
	}

	@Nested
	class Create {

		@Test
		void makesEveryDayAndPutsEachPlaceOnce() {
			MvcTestResult result = route(placeA, placeB, placeC, placeD);

			assertThat(result).hasStatus(HttpStatus.CREATED);
			assertThat(read(result, "$.days[*].dayNumber")).isEqualTo(List.of(1, 2, 3));
			assertThat(read(result, "$.days[*].date")).isEqualTo(List.of("2026-11-10", "2026-11-11", "2026-11-12"));
			List<Integer> itemCounts = list(result, "$.days[*].itemCount");
			assertThat(itemCounts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(savedPlaceIds().size());
			assertThat(read(result, "$.unscheduled.length()")).isEqualTo(0);
			assertThat(savedPlaceIds("SELECTED")).containsExactlyInAnyOrder(placeA, placeB, placeC, placeD);
			assertThat(savedPlaceIds()).doesNotHaveDuplicates();
		}

		@Test
		void fewPlacesAreFilledUpToRecommendedMinimumInRecommendationOrder() {
			route(placeA, placeB, placeC, placeD);
			flush();

			// 담은 4곳 + 추천 5곳 = 9곳. 채운 장소는 추천 목록 앞쪽에서 오고, 장소 선택으로 돌아가도 담김으로 보이지 않는다
			List<Long> filled = savedPlaceIds("AUTO_FILL");
			assertThat(filled).hasSize(5).doesNotContain(placeA, placeB, placeC, placeD);
			MvcTestResult candidates = mvc.get().uri("/api/trips/{tripId}/candidates?size=14", tripId)
					.header("X-Dev-User-Id", userId).exchange();
			assertThat(ids(candidates, "$.items[*].place.placeId")).containsAll(filled);
			for (Long placeId : filled) {
				assertThat(read(candidates, "$.items[?(@.place.placeId == " + placeId + ")].inTrip"))
						.isEqualTo(List.of(false));
			}
			List<String> origins = new ArrayList<>();
			for (int day = 1; day <= 3; day++) {
				origins.addAll(list(day(day), "$.items[*].origin"));
			}
			assertThat(origins).containsOnly("SELECTED", "AUTO_FILL").contains("AUTO_FILL");
		}

		@Test
		void enoughPlacesAreNotFilled() {
			long[] nine = {placeA, placeB, placeC, placeD,
					insertPlace("TR5", "시험 장소 5", 0.0025, "UNKNOWN", 30),
					insertPlace("TR6", "시험 장소 6", -0.0025, "UNKNOWN", 30),
					insertPlace("TR7", "시험 장소 7", 0.0035, "UNKNOWN", 30),
					insertPlace("TR8", "시험 장소 8", -0.0035, "UNKNOWN", 30),
					insertPlace("TR9", "시험 장소 9", 0.0045, "UNKNOWN", 30)};
			flush();

			assertThat(route(nine)).hasStatus(HttpStatus.CREATED);

			assertThat(savedPlaceIds("AUTO_FILL")).isEmpty();
			assertThat(savedPlaceIds()).hasSize(9);
		}

		@Test
		void tripGetsTitleAndRouteCreated() {
			MvcTestResult result = route(placeA, placeB);

			assertThat(read(result, "$.trip.tripId")).isEqualTo((int) tripId);
			assertThat(read(result, "$.trip.title")).isEqualTo("서울 2박 3일 여행");
			assertThat(read(result, "$.trip.routeCreated")).isEqualTo(true);
			flush();
			assertThat(read(mvc.get().uri("/api/trips/{tripId}", tripId).header("X-Dev-User-Id", userId).exchange(),
					"$.title")).isEqualTo("서울 2박 3일 여행");
		}

		@Test
		void englishTitle() {
			MvcTestResult result = mvc.post().uri("/api/trips/{tripId}/route", tripId).header("X-Dev-User-Id", userId)
					.header("Accept-Language", "en").contentType(MediaType.APPLICATION_JSON)
					.content(body(placeA)).exchange();

			assertThat(read(result, "$.trip.title")).isEqualTo("Seoul 3-Day Trip");
		}

		@Test
		void dayTripTitle() {
			long other = insertUser();
			long dayTripId = createTrip(other, TRIP.replace("2026-11-12", "2026-11-10"));

			MvcTestResult result = mvc.post().uri("/api/trips/{tripId}/route", dayTripId).header("X-Dev-User-Id", other)
					.contentType(MediaType.APPLICATION_JSON).content(body(placeA)).exchange();

			assertThat(read(result, "$.trip.title")).isEqualTo("서울 당일 여행");
			assertThat(read(result, "$.days.length()")).isEqualTo(1);
		}

		@Test
		void savedDaysChainTimesAndHaveNoWarnings() {
			route(placeA, placeB, placeC, placeD);
			flush();

			for (int day = 1; day <= 3; day++) {
				MvcTestResult result = day(day);
				assertThat(result).hasStatusOk();
				List<Map<String, Object>> items = list(result, "$.items");
				List<Map<String, Object>> legs = list(result, "$.legs");
				assertThat(legs).hasSize(items.isEmpty() ? 0 : items.size() + 1);
				assertThat(read(result, "$.items[*].warnings[*]")).isEqualTo(List.of());
				if (items.isEmpty()) {
					assertThat(read(result, "$.departTime")).isNull();
					continue;
				}
				assertThat(read(result, "$.departTime")).isEqualTo("09:00");
				LocalTime previous = LocalTime.of(9, 0);
				for (int i = 0; i < items.size(); i++) {
					LocalTime arrive = LocalTime.parse((String) items.get(i).get("arriveTime"));
					assertThat(arrive).isAfterOrEqualTo(previous.plusMinutes((Integer) legs.get(i).get("durationMinutes")));
					previous = LocalTime.parse((String) items.get(i).get("departTime"));
				}
				LocalTime back = previous.plusMinutes((Integer) legs.getLast().get("durationMinutes"));
				assertThat(read(result, "$.returnTime")).isEqualTo(back.toString());
				assertThat(back).isBeforeOrEqualTo(LocalTime.of(21, 0));
			}
		}

		@Test
		void waitsUntilOpeningTime() {
			route(placeC);
			flush();
			Map<String, Object> c = jdbc.queryForMap("SELECT i.id, d.day_number FROM trip_items i "
					+ "JOIN trip_days d ON d.id = i.trip_day_id WHERE d.trip_id = ? AND i.place_id = ?", tripId, placeC);
			int dayNumber = ((Number) c.get("day_number")).intValue();
			long itemC = ((Number) c.get("id")).longValue();
			List<Long> order = new ArrayList<>(List.of(itemC));
			ids(day(dayNumber), "$.items[*].itemId").stream().filter(id -> id != itemC).forEach(order::add);

			// C를 그날 첫 장소로 옮기면 숙소에서 몇 분 거리라 10:00 전에 도착하고, 여는 시각까지 기다린다 (순서 변경도 같은 규칙)
			MvcTestResult result = reorder(dayNumber, order);

			assertThat(read(result, "$.items[0].arriveTime")).isEqualTo("10:00");
			assertThat(read(result, "$.items[0].departTime")).isEqualTo("10:30");
			assertThat(read(result, "$.items[0].warnings.length()")).isEqualTo(0);
		}

		@Test
		void placeClosedForTheWholeTripIsUnscheduledAndNotSaved() {
			long sundayOnly = insertPlace("TRS", "일요일만 여는 곳", 0.001, "STRUCTURED", 60);
			jdbc.update("INSERT INTO place_opening_hours (place_id, day_of_week, open_time, close_time) "
					+ "VALUES (?, 7, '09:00', '18:00')", sundayOnly);
			flush();

			MvcTestResult result = route(placeA, sundayOnly);

			assertThat(result).hasStatus(HttpStatus.CREATED);
			assertThat(read(result, "$.unscheduled.length()")).isEqualTo(1);
			assertThat(read(result, "$.unscheduled[0].place.placeId")).isEqualTo((int) sundayOnly);
			assertThat(read(result, "$.unscheduled[0].place.name")).isEqualTo("일요일만 여는 곳");
			assertThat(read(result, "$.unscheduled[0].reason")).isEqualTo("CLOSED_DURING_TRIP");
			assertThat(savedPlaceIds("SELECTED")).containsExactly(placeA);
			assertThat(savedPlaceIds()).doesNotContain(sundayOnly);
		}

		@Test
		void callingAgainReplacesTheRouteAndResetsReorderedDay() {
			// 장소 수가 일수보다 많아야 한 날에 여럿이 놓인다: 당일 여행으로 바꿔 부른다
			userId = insertUser();
			tripId = createTrip(userId, TRIP.replace("2026-11-12", "2026-11-10"));
			route(placeA, placeC, placeD);
			flush();
			List<Long> planned = itemPlaceIds(day(1));
			List<Long> itemIds = ids(day(1), "$.items[*].itemId");
			assertThat(itemIds).hasSize(3);
			assertThat(reorder(1, itemIds.reversed())).hasStatusOk();
			flush();
			assertThat(itemPlaceIds(day(1))).isNotEqualTo(planned);

			MvcTestResult again = route(placeA, placeC, placeD);
			flush();

			assertThat(again).hasStatus(HttpStatus.CREATED);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_days WHERE trip_id = ?", Integer.class, tripId))
					.isEqualTo(1);
			assertThat(itemPlaceIds(day(1))).isEqualTo(planned);
		}

		@Test
		void callingAgainKeepsTheDayCount() {
			route(placeA, placeB);
			flush();

			route(placeA, placeB, placeC, placeD);
			flush();

			assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_days WHERE trip_id = ?", Integer.class, tripId))
					.isEqualTo(3);
		}

		@Test
		void callingWithOtherPlacesKeepsOnlyThem() {
			route(placeA, placeB, placeC);
			flush();

			route(placeD);
			flush();

			assertThat(savedPlaceIds("SELECTED")).containsExactly(placeD);
			assertThat(savedPlaceIds()).doesNotContain(placeA, placeB, placeC);
		}

		@Test
		void loginIsRequired() {
			assertThat(mvc.post().uri("/api/trips/{tripId}/route", tripId).contentType(MediaType.APPLICATION_JSON)
					.content(body(placeA))).hasStatus(HttpStatus.UNAUTHORIZED);
		}
	}

	@Nested
	class Errors {

		@Test
		void emptyNullOrMissingPlaceIdsIsBadRequest() {
			assertError(raw("{\"placeIds\": []}"), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
			assertError(raw("{\"placeIds\": [" + placeA + ", null]}"), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
			assertError(raw("{}"), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
		}

		@Test
		void duplicatePlaceIsBadRequest() {
			MvcTestResult result = route(placeA, placeB, placeA);

			assertError(result, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
			assertThat(read(result, "$.fields[0].field")).isEqualTo("placeIds");
		}

		@Test
		void moreThanDaysTimesMaxPerDayIsTooMany() {
			// 3일 × 6곳 = 18곳을 넘는다. 장소 수는 후보 범위보다 먼저 본다
			long[] nineteen = LongStream.rangeClosed(1, 19).map(i -> -i).toArray();

			assertError(route(nineteen), HttpStatus.BAD_REQUEST, "TOO_MANY_PLACES");
		}

		@Test
		void placesOutsideCandidateScopeAreNotSelectable() {
			long lodging = jdbc.queryForObject("SELECT id FROM places WHERE content_type_id = 32 LIMIT 1", Long.class);
			long excluded = jdbc.queryForObject("SELECT p.id FROM places p WHERE NOT (" + PlaceSql.NOT_EXCLUDED
					+ ") LIMIT 1", Long.class);
			long inactive = insertPlace("TRI", "사라진 장소", 0.001, "UNKNOWN", 60);
			jdbc.update("UPDATE places SET active = false WHERE id = ?", inactive);
			long festivalAfterTrip = insertPlace("TRF", "기간 밖 축제", 0.001, "UNKNOWN", 60);
			jdbc.update("UPDATE places SET event_start_date = '2026-12-01', event_end_date = '2026-12-03' WHERE id = ?",
					festivalAfterTrip);
			long missing = placeD + 1_000_000;
			flush();

			MvcTestResult result = route(placeA, lodging, excluded, placeB, inactive, festivalAfterTrip, missing);

			assertError(result, HttpStatus.BAD_REQUEST, "PLACE_NOT_SELECTABLE");
			assertThat(ids(result, "$.placeIds")).containsExactly(lodging, excluded, inactive, festivalAfterTrip, missing);
		}

		@Test
		void festivalOverlappingTheTripIsSelectable() {
			long festival = insertPlace("TRG", "기간 안 축제", 0.001, "UNKNOWN", 60);
			jdbc.update("UPDATE places SET event_start_date = '2026-11-12', event_end_date = '2026-11-20' WHERE id = ?",
					festival);
			flush();

			MvcTestResult result = route(festival);

			assertThat(result).hasStatus(HttpStatus.CREATED);
			// 축제 기간과 겹치는 날은 3일차(11-12)뿐이다
			assertThat(jdbc.queryForObject("SELECT d.day_number FROM trip_items i JOIN trip_days d ON d.id = i.trip_day_id "
					+ "WHERE d.trip_id = ? AND i.place_id = ?", Integer.class, tripId, festival)).isEqualTo(3);
		}

		@Test
		void othersTripIsNotFound() {
			long other = insertUser();

			assertError(mvc.post().uri("/api/trips/{tripId}/route", tripId).header("X-Dev-User-Id", other)
					.contentType(MediaType.APPLICATION_JSON).content(body(placeA)).exchange(),
					HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND");
		}

		@Test
		void confirmedTripCannotBeEdited() {
			confirm();

			assertError(route(placeA), HttpStatus.CONFLICT, "TRIP_CONFIRMED");
		}

		@Test
		void tripIsCheckedBeforePlaces() {
			long other = insertUser();
			long[] nineteen = LongStream.rangeClosed(1, 19).map(i -> -i).toArray();
			assertError(mvc.post().uri("/api/trips/{tripId}/route", tripId).header("X-Dev-User-Id", other)
					.contentType(MediaType.APPLICATION_JSON).content(body(nineteen)).exchange(),
					HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND");

			confirm();
			assertError(route(placeA, placeA), HttpStatus.CONFLICT, "TRIP_CONFIRMED");
		}

		@Test
		void duplicateIsCheckedBeforeCount() {
			long[] duplicated = LongStream.rangeClosed(1, 19).map(i -> i == 19 ? -1 : -i).toArray();

			assertError(route(duplicated), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
		}
	}

	// --- 요청

	private MvcTestResult route(long... placeIds) {
		return mvc.post().uri("/api/trips/{tripId}/route", tripId).header("X-Dev-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON).content(body(placeIds)).exchange();
	}

	private MvcTestResult raw(String content) {
		return mvc.post().uri("/api/trips/{tripId}/route", tripId).header("X-Dev-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON).content(content).exchange();
	}

	private static String body(long... placeIds) {
		return "{\"placeIds\": [" + Arrays.stream(placeIds).mapToObj(String::valueOf).collect(Collectors.joining(","))
				+ "]}";
	}

	private MvcTestResult day(int dayNumber) {
		return mvc.get().uri("/api/trips/{tripId}/days/{dayNumber}", tripId, dayNumber)
				.header("X-Dev-User-Id", userId).exchange();
	}

	private MvcTestResult reorder(int dayNumber, List<Long> itemIds) {
		return mvc.put().uri("/api/trips/{tripId}/days/{dayNumber}/order", tripId, dayNumber)
				.header("X-Dev-User-Id", userId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"itemIds\": " + itemIds + "}").exchange();
	}

	// --- 데이터

	private long insertUser() {
		return jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}

	private long createTrip(long ownerId, String request) {
		assertThat(mvc.post().uri("/api/trips").header("X-Dev-User-Id", ownerId)
				.contentType(MediaType.APPLICATION_JSON).content(request).exchange()).hasStatus(HttpStatus.CREATED);
		flush();
		return jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, ownerId);
	}

	/** 숙소에서 위도로 dLat만큼 떨어진 시험 장소. 후보 범위에 드는 덤프 관광지 하나의 분류를 빌린다 */
	private long insertPlace(String contentId, String name, double dLat, String hoursType, int stayMinutes) {
		Map<String, Object> classes = jdbc.queryForMap("SELECT p.class1_code, p.class2_code, p.class3_code FROM places p "
				+ "WHERE p.content_type_id <> 32 AND p.class1_code <> 'AC' AND " + PlaceSql.NOT_EXCLUDED + " LIMIT 1");
		return jdbc.queryForObject("""
				INSERT INTO places (content_id, content_type_id, class1_code, class2_code, class3_code, name_ko,
				                    ldong_regn_cd, ldong_signgu_cd, location, hours_type, fee_type, stay_minutes,
				                    stay_basis, signal_count, source_modified_at, synced_at, active)
				VALUES (?, 12, ?, ?, ?, ?, '11', '140', ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, 'UNKNOWN',
				        ?, 'CLASS', 0, now(), now(), true)
				RETURNING id
				""", Long.class, contentId + (System.nanoTime() % 1_000_000_000_000L), classes.get("class1_code"),
				classes.get("class2_code"), classes.get("class3_code"), name, stayLng, stayLat + dLat, hoursType,
				stayMinutes);
	}

	private void insertHoursEveryDay(long placeId, String open, String close) {
		for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
			jdbc.update("INSERT INTO place_opening_hours (place_id, day_of_week, open_time, close_time) "
					+ "VALUES (?, ?, ?::time, ?::time)", placeId, dayOfWeek, open, close);
		}
	}

	private List<Long> savedPlaceIds() {
		return jdbc.queryForList("SELECT i.place_id FROM trip_items i JOIN trip_days d ON d.id = i.trip_day_id "
				+ "WHERE d.trip_id = ?", Long.class, tripId);
	}

	private List<Long> savedPlaceIds(String origin) {
		return jdbc.queryForList("SELECT i.place_id FROM trip_items i JOIN trip_days d ON d.id = i.trip_day_id "
				+ "WHERE d.trip_id = ? AND i.origin = ?", Long.class, tripId, origin);
	}

	private void confirm() {
		jdbc.update("UPDATE trips SET status = 'CONFIRMED', confirmed_at = now() WHERE id = ?", tripId);
		flush();
	}

	/** 테스트의 요청들은 한 트랜잭션에서 돌므로, 요청이 끝난 것처럼 DB에 쓰고 영속성 컨텍스트를 비운다 */
	private void flush() {
		entityManager.flush();
		entityManager.clear();
	}

	// --- 응답 읽기

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Object read(MvcTestResult result, String path) {
		return JsonPath.read(content(result), path);
	}

	private static <T> List<T> list(MvcTestResult result, String path) {
		return JsonPath.read(content(result), path);
	}

	private static List<Long> ids(MvcTestResult result, String path) {
		List<Number> values = JsonPath.read(content(result), path);
		return values.stream().map(Number::longValue).toList();
	}

	private static List<Long> itemPlaceIds(MvcTestResult result) {
		return new ArrayList<>(ids(result, "$.items[*].place.placeId"));
	}

	private static void assertError(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
	}
}
