package com.tukorea.gdgoc.koready.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
 * 일자별 일정 조회·순서 변경·장소 삭제. 장소는 테스트가 직접 넣고(숙소 근처 가까운 곳 둘, 먼 곳 하나), 모두 롤백된다.
 * 1일차(2026-11-10)에 A(가까움, 60분) → B(멂, 30분) → C(가까움, 30분, 09:00~10:00에만 엶) 순서로 담겨 있다.
 */
@IntegrationTest
class TripDayTest {

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
	long dayId;
	long placeA;
	long placeB;
	long placeC;
	long itemA;
	long itemB;
	long itemC;

	@BeforeEach
	void seed() {
		userId = insertUser();
		assertThat(mvc.post().uri("/api/trips").header("X-Dev-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON).content(TRIP).exchange()).hasStatus(HttpStatus.CREATED);
		tripId = jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, userId);

		Map<String, Object> stay = jdbc.queryForMap(
				"SELECT ST_Y(stay_location::geometry) AS lat, ST_X(stay_location::geometry) AS lng FROM trips WHERE id = ?",
				tripId);
		double lat = ((Number) stay.get("lat")).doubleValue();
		double lng = ((Number) stay.get("lng")).doubleValue();
		// 위도 0.002 ≈ 220m(도보), 0.03 ≈ 3.3km(대중교통)
		placeA = insertPlace("TDA", "시험 장소 A", lat + 0.002, lng, "UNKNOWN", 60);
		placeB = insertPlace("TDB", "시험 장소 B", lat + 0.03, lng, "UNKNOWN", 30);
		placeC = insertPlace("TDC", "시험 장소 C", lat + 0.003, lng, "STRUCTURED", 30);
		for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
			jdbc.update("INSERT INTO place_opening_hours (place_id, day_of_week, open_time, close_time) "
					+ "VALUES (?, ?, '09:00', '10:00')", placeC, dayOfWeek);
		}

		dayId = insertDay(tripId, 1, "2026-11-10");
		itemA = insertItem(dayId, 1, placeA, 60);
		itemB = insertItem(dayId, 2, placeB, 30);
		itemC = insertItem(dayId, 3, placeC, 30);
		// 시각과 구간은 순서 변경이 계산한다: 처음 순서를 그대로 보내 채운다
		assertThat(reorder(tripId, 1, itemA, itemB, itemC)).hasStatusOk();
		flush();
	}

	@Nested
	class GetDay {

		@Test
		void showsItemsLegsAndSummary() {
			MvcTestResult result = get(tripId, 1);

			assertThat(result).hasStatusOk();
			assertThat(read(result, "$.dayNumber")).isEqualTo(1);
			assertThat(read(result, "$.date")).isEqualTo("2026-11-10");
			assertThat(read(result, "$.departTime")).isEqualTo("09:00");
			assertThat(ids(result, "$.items[*].itemId")).containsExactly(itemA, itemB, itemC);
			assertThat(read(result, "$.items[0].place.name")).isEqualTo("시험 장소 A");
			assertThat(read(result, "$.items[0].stayMinutes")).isEqualTo(60);
			assertThat(read(result, "$.summary.itemCount")).isEqualTo(3);
			assertThat(read(result, "$.summary.totalStayMinutes")).isEqualTo(120);
			assertThat(read(result, "$.legs.length()")).isEqualTo(4);
			assertThat(read(result, "$.legs[0].fromItemId")).isNull();
			assertThat(read(result, "$.legs[3].toItemId")).isNull();
			assertThat(read(result, "$.stay.name")).isEqualTo("명동");
		}

		@Test
		void timesAreChainedFromDepartTime() {
			MvcTestResult result = get(tripId, 1);

			// 숙소 출발 09:00 → (이동) → A 도착, 체류 60분 → 출발
			int travelToA = (Integer) read(result, "$.legs[0].durationMinutes");
			assertThat(read(result, "$.items[0].arriveTime")).isEqualTo(time(9 * 60 + travelToA));
			assertThat(read(result, "$.items[0].departTime")).isEqualTo(time(9 * 60 + travelToA + 60));
		}

		@Test
		void walkingIsFreeAndUnknownTransitFareMakesTotalNull() {
			MvcTestResult result = get(tripId, 1);

			assertThat(read(result, "$.legs[0].mode")).isEqualTo("WALK");
			assertThat(read(result, "$.legs[0].fare.amount")).isEqualTo(0);
			assertThat(read(result, "$.legs[1].mode")).isEqualTo("TRANSIT");
			assertThat(read(result, "$.legs[1].fare")).isNull();
			assertThat(read(result, "$.legs[1].estimated")).isEqualTo(true);
			assertThat(read(result, "$.summary.farePerPerson")).isNull();
		}

		@Test
		void totalFareIsSumWhenEveryFareIsKnown() {
			jdbc.update("UPDATE legs SET fare_krw = 1500 WHERE trip_day_id = ? AND fare_krw IS NULL", dayId);

			MvcTestResult result = get(tripId, 1);

			Integer expected = jdbc.queryForObject("SELECT sum(fare_krw) FROM legs WHERE trip_day_id = ?", Integer.class,
					dayId);
			assertThat(read(result, "$.summary.farePerPerson.amount")).isEqualTo(expected);
			assertThat(read(result, "$.summary.farePerPerson.currency")).isEqualTo("KRW");
		}

		@Test
		void warnsOnlyForItemsOutsideOpeningHours() {
			MvcTestResult result = get(tripId, 1);

			// C는 09:00~10:00에만 열지만 세 번째로 가서 늦게 도착한다. A·B는 운영시간 정보가 없어 제약이 없다
			assertThat(read(result, "$.items[2].warnings[0]")).isEqualTo("OUTSIDE_OPENING_HOURS");
			assertThat(read(result, "$.items[0].warnings.length()")).isEqualTo(0);
			assertThat(read(result, "$.items[1].warnings.length()")).isEqualTo(0);
		}

		@Test
		void confirmedTripCanBeViewed() {
			confirm();

			assertThat(get(tripId, 1)).hasStatusOk();
		}

		@Test
		void tripWithoutRouteIsConflict() {
			long otherUser = insertUser();
			createTrip(otherUser);
			long routelessTripId = tripOf(otherUser);

			assertError(mvc.get().uri("/api/trips/{tripId}/days/{day}", routelessTripId, 1)
					.header("X-Dev-User-Id", otherUser).exchange(), HttpStatus.CONFLICT, "ROUTE_NOT_CREATED");
		}

		@Test
		void missingDayIsNotFound() {
			assertError(get(tripId, 9), HttpStatus.NOT_FOUND, "DAY_NOT_FOUND");
			assertError(get(tripId, 0), HttpStatus.NOT_FOUND, "DAY_NOT_FOUND");
			assertError(get(tripId, 100000), HttpStatus.NOT_FOUND, "DAY_NOT_FOUND");
		}

		@Test
		void othersTripIsNotFoundAndLoginIsRequired() {
			long other = insertUser();

			assertError(mvc.get().uri("/api/trips/{tripId}/days/1", tripId).header("X-Dev-User-Id", other).exchange(),
					HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND");
			assertThat(mvc.get().uri("/api/trips/{tripId}/days/1", tripId)).hasStatus(HttpStatus.UNAUTHORIZED);
		}
	}

	@Nested
	class Reorder {

		@Test
		void changesOrderKeepsItemIdsAndRecalculates() {
			MvcTestResult before = get(tripId, 1);

			MvcTestResult result = reorder(tripId, 1, itemC, itemA, itemB);

			assertThat(result).hasStatusOk();
			assertThat(ids(result, "$.items[*].itemId")).containsExactly(itemC, itemA, itemB);
			assertThat(read(result, "$.items[0].seq")).isEqualTo(1);
			assertThat(read(result, "$.items[2].seq")).isEqualTo(3);
			assertThat(read(result, "$.legs.length()")).isEqualTo(4);
			assertThat(read(result, "$.legs[0].toItemId")).isEqualTo((int) itemC);
			assertThat(read(result, "$.items[0].arriveTime")).isNotEqualTo(read(before, "$.items[0].arriveTime"));
			// 같은 항목들이니 체류시간 합은 그대로, 구간은 새 순서로 다시 만든다
			assertThat(read(result, "$.summary.totalStayMinutes")).isEqualTo(120);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_items WHERE trip_day_id = ?", Integer.class, dayId))
					.isEqualTo(3);
		}

		@Test
		void warningFollowsNewOrder() {
			MvcTestResult result = reorder(tripId, 1, itemC, itemA, itemB);

			// C를 맨 앞으로 옮기면 09시대에 도착해 운영시간 안이다
			assertThat(read(result, "$.items[0].warnings.length()")).isEqualTo(0);
		}

		@Test
		void restoringOriginalOrderGivesOriginalValues() {
			String original = body(get(tripId, 1));
			assertThat(reorder(tripId, 1, itemC, itemB, itemA)).hasStatusOk();
			flush();

			MvcTestResult restored = reorder(tripId, 1, itemA, itemB, itemC);

			assertThat(body(restored)).isEqualTo(original);
		}

		@Test
		void mismatchedItemsAreRejected() {
			long otherDay = insertDay(tripId, 2, "2026-11-11");
			long otherDayItem = insertItem(otherDay, 1, placeA, 60);

			assertError(reorder(tripId, 1, itemA, itemB), HttpStatus.BAD_REQUEST, "ORDER_MISMATCH");
			assertError(reorder(tripId, 1, itemA, itemA, itemB, itemC), HttpStatus.BAD_REQUEST, "ORDER_MISMATCH");
			assertError(reorder(tripId, 1, itemA, itemB, otherDayItem), HttpStatus.BAD_REQUEST, "ORDER_MISMATCH");
		}

		@Test
		void missingItemIdsIsBadRequest() {
			assertError(mvc.put().uri("/api/trips/{tripId}/days/1/order", tripId).header("X-Dev-User-Id", userId)
					.contentType(MediaType.APPLICATION_JSON).content("{}").exchange(),
					HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
		}

		@Test
		void withoutContentTypeIsUnsupported() {
			assertThat(mvc.put().uri("/api/trips/{tripId}/days/1/order", tripId).header("X-Dev-User-Id", userId)
					.content("{\"itemIds\": []}").exchange()).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		}

		@Test
		void confirmedTripCannotBeEdited() {
			confirm();

			assertError(reorder(tripId, 1, itemC, itemA, itemB), HttpStatus.CONFLICT, "TRIP_CONFIRMED");
		}

		@Test
		void missingDayIsNotFound() {
			assertError(reorder(tripId, 9, itemA), HttpStatus.NOT_FOUND, "DAY_NOT_FOUND");
		}
	}

	@Nested
	class DeleteItem {

		@Test
		void removesItemAndRecalculatesDay() {
			MvcTestResult result = delete(tripId, itemB);

			assertThat(result).hasStatusOk();
			assertThat(ids(result, "$.items[*].itemId")).containsExactly(itemA, itemC);
			assertThat(read(result, "$.items[1].seq")).isEqualTo(2);
			assertThat(read(result, "$.legs.length()")).isEqualTo(3);
			assertThat(read(result, "$.summary.itemCount")).isEqualTo(2);
			assertThat(read(result, "$.summary.totalStayMinutes")).isEqualTo(90);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_items WHERE id = ?", Integer.class, itemB))
					.isZero();
		}

		@Test
		void mealItemCanBeRemovedToo() {
			jdbc.update("UPDATE trip_items SET origin = 'MEAL', meal_type = 'LUNCH' WHERE id = ?", itemB);
			flush();

			MvcTestResult result = delete(tripId, itemB);

			assertThat(result).hasStatusOk();
			assertThat(ids(result, "$.items[*].itemId")).containsExactly(itemA, itemC);
		}

		@Test
		void dayStaysWhenLastItemIsRemoved() {
			delete(tripId, itemA);
			flush();
			delete(tripId, itemB);
			flush();

			MvcTestResult result = delete(tripId, itemC);

			assertThat(result).hasStatusOk();
			assertThat(read(result, "$.items.length()")).isEqualTo(0);
			assertThat(read(result, "$.legs.length()")).isEqualTo(0);
			assertThat(read(result, "$.departTime")).isNull();
			assertThat(read(result, "$.returnTime")).isNull();
			assertThat(read(result, "$.summary.farePerPerson.amount")).isEqualTo(0);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_days WHERE id = ?", Integer.class, dayId))
					.isEqualTo(1);
		}

		@Test
		void itemOfAnotherTripOrMissingIsNotFound() {
			long otherUser = insertUser();
			createTrip(otherUser);
			long otherTripId = tripOf(otherUser);

			assertError(mvc.delete().uri("/api/trips/{tripId}/items/{itemId}", otherTripId, itemA)
					.header("X-Dev-User-Id", otherUser).exchange(), HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND");
			assertError(delete(tripId, itemC + 100000), HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND");
		}

		@Test
		void confirmedTripCannotBeEdited() {
			confirm();

			assertError(delete(tripId, itemA), HttpStatus.CONFLICT, "TRIP_CONFIRMED");
		}
	}

	// --- 요청

	private MvcTestResult get(long tripId, int dayNumber) {
		return mvc.get().uri("/api/trips/{tripId}/days/{dayNumber}", tripId, dayNumber)
				.header("X-Dev-User-Id", userId).exchange();
	}

	private MvcTestResult reorder(long tripId, int dayNumber, long... itemIds) {
		String ids = String.join(",", java.util.Arrays.stream(itemIds).mapToObj(String::valueOf).toList());
		return mvc.put().uri("/api/trips/{tripId}/days/{dayNumber}/order", tripId, dayNumber)
				.header("X-Dev-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"itemIds\": [" + ids + "]}")
				.exchange();
	}

	private MvcTestResult delete(long tripId, long itemId) {
		return mvc.delete().uri("/api/trips/{tripId}/items/{itemId}", tripId, itemId)
				.header("X-Dev-User-Id", userId).exchange();
	}

	// --- 데이터

	private long insertUser() {
		return jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}

	private void createTrip(long asUserId) {
		assertThat(mvc.post().uri("/api/trips").header("X-Dev-User-Id", asUserId)
				.contentType(MediaType.APPLICATION_JSON).content(TRIP).exchange()).hasStatus(HttpStatus.CREATED);
		flush();
	}

	private long tripOf(long ownerId) {
		return jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, ownerId);
	}

	/** 덤프의 관광지 하나가 쓰는 분류를 빌려 시험 장소를 넣는다 */
	private long insertPlace(String contentId, String name, double lat, double lng, String hoursType, int stayMinutes) {
		Map<String, Object> classes = jdbc.queryForMap(
				"SELECT class1_code, class2_code, class3_code FROM places WHERE content_type_id <> 32 LIMIT 1");
		return jdbc.queryForObject("""
				INSERT INTO places (content_id, content_type_id, class1_code, class2_code, class3_code, name_ko,
				                    ldong_regn_cd, ldong_signgu_cd, location, hours_type, fee_type, stay_minutes,
				                    stay_basis, signal_count, source_modified_at, synced_at, active)
				VALUES (?, 12, ?, ?, ?, ?, '11', '140', ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, 'UNKNOWN',
				        ?, 'CLASS', 0, now(), now(), true)
				RETURNING id
				""", Long.class, contentId + (System.nanoTime() % 1_000_000_000_000L), classes.get("class1_code"),
				classes.get("class2_code"), classes.get("class3_code"), name, lng, lat, hoursType, stayMinutes);
	}

	private long insertDay(long tripId, int dayNumber, String date) {
		return jdbc.queryForObject("INSERT INTO trip_days (trip_id, day_number, trip_date) VALUES (?, ?, ?::date) RETURNING id",
				Long.class, tripId, dayNumber, date);
	}

	private long insertItem(long dayId, int seq, long placeId, int stayMinutes) {
		return jdbc.queryForObject("""
				INSERT INTO trip_items (trip_day_id, seq, place_id, origin, arrive_time, depart_time, stay_minutes)
				VALUES (?, ?, ?, 'SELECTED', '00:00', '00:00', ?) RETURNING id
				""", Long.class, dayId, seq, placeId, stayMinutes);
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

	private static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Object read(MvcTestResult result, String path) {
		return JsonPath.read(body(result), path);
	}

	private static List<Long> ids(MvcTestResult result, String path) {
		List<Number> values = JsonPath.read(body(result), path);
		return values.stream().map(Number::longValue).toList();
	}

	private static String time(int minutes) {
		return "%02d:%02d".formatted(minutes / 60, minutes % 60);
	}

	private static void assertError(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
	}
}
