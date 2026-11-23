package com.tukorea.gdgoc.koready.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.tukorea.gdgoc.koready.domain.meta.entity.Area;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * 여행 목록. "오늘"은 2026-11-01이다 ({@link com.tukorea.gdgoc.koready.support.TestClockConfig}).
 * 여행은 API로 만든 뒤 상태·기간을 DB에서 바꾸고, 장소는 테스트가 직접 넣는다. 모두 롤백된다.
 */
@IntegrationTest
class TripListTest {

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

	@BeforeEach
	void createUser() {
		userId = insertUser();
	}

	@Test
	void draftIsNotListed() {
		createTrip(userId);

		assertThat(read(list("CURRENT"), "$.items.length()")).isEqualTo(0);
		assertThat(read(list("PAST"), "$.items.length()")).isEqualTo(0);
		assertThat(read(list("CURRENT"), "$.totalCount")).isEqualTo(0);
	}

	@Test
	void currentIsDefaultAndHasTripSummaryFields() {
		long tripId = confirmedTrip(userId, "2026-11-10", "2026-11-12");

		MvcTestResult result = mvc.get().uri("/api/trips").header("X-Dev-User-Id", userId).exchange();

		assertThat(result).hasStatusOk();
		assertThat(ids(result, "$.items[*].tripId")).containsExactly(tripId);
		assertThat(read(result, "$.items[0].ticketNo")).isEqualTo("TK-%04d".formatted(tripId));
		assertThat(read(result, "$.items[0].status")).isEqualTo("CONFIRMED");
		assertThat(read(result, "$.items[0].progress")).isEqualTo("UPCOMING");
		assertThat(read(result, "$.items[0].stay.name")).isEqualTo("명동");
		assertThat(read(result, "$.items[0].expenseSaved")).isEqualTo(false);
		assertThat(read(result, "$.totalCount")).isEqualTo(1);
		assertThat(read(result, "$.hasNext")).isEqualTo(false);
	}

	@Test
	void tripEndingTodayIsCurrentAndTripEndedYesterdayIsPast() {
		long ended = confirmedTrip(userId, "2026-10-29", "2026-10-31");
		long endingToday = confirmedTrip(userId, "2026-10-30", "2026-11-01");

		assertThat(ids(list("CURRENT"), "$.items[*].tripId")).containsExactly(endingToday);
		assertThat(ids(list("PAST"), "$.items[*].tripId")).containsExactly(ended);
		assertThat(read(list("PAST"), "$.items[0].progress")).isEqualTo("COMPLETED");
	}

	@Test
	void pastIsOrderedByLatestEndDateAndPaged() {
		long older = confirmedTrip(userId, "2026-09-01", "2026-09-03");
		long latest = confirmedTrip(userId, "2026-10-20", "2026-10-22");
		long middle = confirmedTrip(userId, "2026-10-01", "2026-10-03");

		assertThat(ids(list("PAST"), "$.items[*].tripId")).containsExactly(latest, middle, older);

		MvcTestResult secondPage = mvc.get().uri("/api/trips?period=PAST&page=1&size=2")
				.header("X-Dev-User-Id", userId).exchange();
		assertThat(ids(secondPage, "$.items[*].tripId")).containsExactly(older);
		assertThat(read(secondPage, "$.totalCount")).isEqualTo(3);
		assertThat(read(secondPage, "$.hasNext")).isEqualTo(false);
	}

	@Test
	void pastHasNoDaySummariesAndTellsWhetherExpensesAreSaved() {
		long saved = confirmedTrip(userId, "2026-10-20", "2026-10-22");
		long notSaved = confirmedTrip(userId, "2026-10-01", "2026-10-03");
		insertItem(insertDay(saved, 1, "2026-10-20"), 1, insertPlace("시험 장소", Area.MYEONGDONG, true), null);
		jdbc.update("INSERT INTO trip_expenses (trip_id, item, amount_krw, seq) VALUES (?, 'FOOD', 30000, 1)", saved);
		flush();

		MvcTestResult result = list("PAST");

		assertThat(ids(result, "$.items[*].tripId")).containsExactly(saved, notSaved);
		assertThat(read(result, "$.items[*].expenseSaved")).isEqualTo(List.of(true, false));
		assertThat(read(result, "$.items[0].daySummaries.length()")).isEqualTo(0);
	}

	@Test
	void daySummaryPicksSightsAreasAndFirstPhoto() {
		long tripId = confirmedTrip(userId, "2026-11-10", "2026-11-12");
		long day1 = insertDay(tripId, 1, "2026-11-10");
		// 식사 자리는 주요 장소·대표 사진에서 빠지고, 사진이 없는 장소는 대표 사진을 건너뛴다
		insertItem(day1, 1, insertPlace("점심 식당", Area.SEONGSU, true), "LUNCH");
		insertItem(day1, 2, insertPlace("사진 없는 곳", Area.SEONGSU, false), null);
		long withPhoto = insertPlace("사진 있는 곳", Area.MYEONGDONG, true);
		insertItem(day1, 3, withPhoto, null);
		insertItem(day1, 4, insertPlace("셋째 곳", Area.SEONGSU, true), null);
		insertItem(day1, 5, insertPlace("넷째 곳", Area.MYEONGDONG, true), null);
		insertDay(tripId, 2, "2026-11-11");
		flush();

		MvcTestResult result = list("CURRENT");

		assertThat(read(result, "$.items[0].daySummaries[*].dayNumber")).isEqualTo(List.of(1, 2));
		assertThat(read(result, "$.items[0].daySummaries[0].date")).isEqualTo("2026-11-10");
		assertThat(read(result, "$.items[0].daySummaries[0].places"))
				.isEqualTo(List.of("사진 없는 곳", "사진 있는 곳", "셋째 곳"));
		assertThat(read(result, "$.items[0].daySummaries[0].areaNames")).isEqualTo(List.of("성수", "명동"));
		assertThat(read(result, "$.items[0].daySummaries[0].image.url")).isEqualTo("https://example.com/" + withPhoto);
		assertThat(read(result, "$.items[0].daySummaries[0].image.license")).isEqualTo("TYPE1");
		// 항목이 없는 날도 일자는 있다
		assertThat(read(result, "$.items[0].daySummaries[1].places.length()")).isEqualTo(0);
		assertThat(read(result, "$.items[0].daySummaries[1].areaNames.length()")).isEqualTo(0);
		assertThat(read(result, "$.items[0].daySummaries[1].image")).isNull();
	}

	@Test
	void areaNamesFollowRequestLanguage() {
		long tripId = confirmedTrip(userId, "2026-11-10", "2026-11-12");
		insertItem(insertDay(tripId, 1, "2026-11-10"), 1, insertPlace("시험 장소", Area.SEONGSU, true), null);
		flush();

		MvcTestResult result = mvc.get().uri("/api/trips").header("X-Dev-User-Id", userId)
				.header("Accept-Language", "en").exchange();

		assertThat(read(result, "$.items[0].daySummaries[0].areaNames")).isEqualTo(List.of("Seongsu"));
	}

	@Test
	void placeFarFromEveryAreaHasNoAreaName() {
		long tripId = confirmedTrip(userId, "2026-11-10", "2026-11-12");
		long day1 = insertDay(tripId, 1, "2026-11-10");
		insertItem(day1, 1, insertPlace("가까운 곳", Area.SEONGSU.getCenter().lat(), Area.SEONGSU.getCenter().lng(), true), null);
		// 북한산 쪽: 어느 지역 중심과도 4km 넘게 떨어져 있다
		insertItem(day1, 2, insertPlace("먼 곳", 37.66, 126.98, true), null);
		flush();

		MvcTestResult result = list("CURRENT");

		assertThat(read(result, "$.items[0].daySummaries[0].places")).isEqualTo(List.of("가까운 곳", "먼 곳"));
		assertThat(read(result, "$.items[0].daySummaries[0].areaNames")).isEqualTo(List.of("성수"));
	}

	@Test
	void budgetIsConvertedToDisplayCurrency() {
		budgetTrip();
		jdbc.update("DELETE FROM exchange_rates WHERE currency = 'USD'");
		jdbc.update("""
				INSERT INTO exchange_rates (rate_date, currency, krw_per_unit, fetched_at)
				VALUES ('2026-10-30', 'USD', 1400, now())
				""");

		MvcTestResult result = listIn("USD");

		assertThat(read(result, "$.items[0].budgetPerPerson.currency")).isEqualTo("USD");
		assertThat(((Number) read(result, "$.items[0].budgetPerPerson.amount")).doubleValue()).isEqualTo(500.0);
	}

	@Test
	void listStillWorksInWonWhenExchangeRateIsMissing() {
		long tripId = budgetTrip();
		jdbc.update("DELETE FROM exchange_rates");

		MvcTestResult result = listIn("USD");

		assertThat(result).hasStatusOk();
		assertThat(ids(result, "$.items[*].tripId")).containsExactly(tripId);
		assertThat(read(result, "$.items[0].budgetPerPerson.currency")).isEqualTo("KRW");
		assertThat(read(result, "$.items[0].budgetPerPerson.amount")).isEqualTo(700000);
	}

	@Test
	void categoriesAndRouteCreatedMatchTripSummary() throws Exception {
		long tripId = confirmedTrip(userId, "2026-11-10", "2026-11-12");
		jdbc.update("INSERT INTO trip_categories (trip_id, class1_code) VALUES (?, 'SH'), (?, 'HS')", tripId, tripId);
		insertDay(tripId, 1, "2026-11-10");
		flush();

		String summary = mvc.get().uri("/api/trips/{tripId}", tripId).header("X-Dev-User-Id", userId).exchange()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);
		MvcTestResult result = list("CURRENT");

		assertThat(read(result, "$.items[0].routeCreated")).isEqualTo(true);
		assertThat(read(result, "$.items[0].categories")).isEqualTo(JsonPath.read(summary, "$.categories"));
		assertThat((List<?>) read(result, "$.items[0].categories")).hasSize(2);
	}

	@Test
	void otherUsersTripsAreNotListed() {
		confirmedTrip(insertUser(), "2026-11-10", "2026-11-12");

		assertThat(read(list("CURRENT"), "$.items.length()")).isEqualTo(0);
	}

	@Test
	void unknownPeriodIsInvalid() {
		assertThat(list("ALL")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code")
				.isEqualTo("INVALID_REQUEST");
	}

	private MvcTestResult listIn(String currency) {
		return mvc.get().uri("/api/trips").header("X-Dev-User-Id", userId).header("X-Currency", currency).exchange();
	}

	/** 1인 희망 예산이 700,000원인 확정 여행 */
	private long budgetTrip() {
		long tripId = confirmedTrip(userId, "2026-11-10", "2026-11-12");
		jdbc.update("UPDATE trips SET budget_per_person_krw = 700000 WHERE id = ?", tripId);
		flush();
		return tripId;
	}

	private MvcTestResult list(String period) {
		return mvc.get().uri("/api/trips?period={period}", period).header("X-Dev-User-Id", userId).exchange();
	}

	// --- 데이터

	private long insertUser() {
		return jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}

	private long createTrip(long asUserId) {
		assertThat(mvc.post().uri("/api/trips").header("X-Dev-User-Id", asUserId)
				.contentType(MediaType.APPLICATION_JSON).content(TRIP).exchange()).hasStatus(HttpStatus.CREATED);
		flush();
		return jdbc.queryForObject("SELECT max(id) FROM trips WHERE user_id = ?", Long.class, asUserId);
	}

	/** 확정한 여행. 만든 뒤 기간을 바꿔 지난 여행도 만든다 (확정 전 여행은 다음 만들기 때 지워지므로 바로 확정한다) */
	private long confirmedTrip(long asUserId, String startDate, String endDate) {
		// 끝나지 않은 확정 여행이 있으면 만들 수 없어, 잠깐 지난 여행으로 돌려 둔다
		List<Long> active = jdbc.queryForList(
				"SELECT id FROM trips WHERE user_id = ? AND status = 'CONFIRMED' AND end_date >= '2026-11-01'", Long.class,
				asUserId);
		Map<Long, Map<String, Object>> dates = new java.util.HashMap<>();
		for (Long id : active) {
			dates.put(id, jdbc.queryForMap("SELECT start_date, end_date FROM trips WHERE id = ?", id));
			jdbc.update("UPDATE trips SET start_date = '2026-01-01', end_date = '2026-01-02' WHERE id = ?", id);
		}
		long tripId = createTrip(asUserId);
		jdbc.update("UPDATE trips SET status = 'CONFIRMED', confirmed_at = now(), start_date = ?::date, end_date = ?::date "
				+ "WHERE id = ?", startDate, endDate, tripId);
		dates.forEach((id, row) -> jdbc.update("UPDATE trips SET start_date = ?, end_date = ? WHERE id = ?",
				row.get("start_date"), row.get("end_date"), id));
		flush();
		return tripId;
	}

	/** 덤프의 관광지 하나가 쓰는 분류를 빌려, 지역 중심 좌표에 시험 장소를 넣는다 */
	private long insertPlace(String name, Area area, boolean withPhoto) {
		return insertPlace(name, area.getCenter().lat(), area.getCenter().lng(), withPhoto);
	}

	private long insertPlace(String name, double lat, double lng, boolean withPhoto) {
		Map<String, Object> classes = jdbc.queryForMap(
				"SELECT class1_code, class2_code, class3_code FROM places WHERE content_type_id <> 32 LIMIT 1");
		long placeId = jdbc.queryForObject("""
				INSERT INTO places (content_id, content_type_id, class1_code, class2_code, class3_code, name_ko,
				                    ldong_regn_cd, ldong_signgu_cd, location, hours_type, fee_type, stay_minutes,
				                    stay_basis, signal_count, source_modified_at, synced_at, active)
				VALUES (?, 12, ?, ?, ?, ?, '11', '140', ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, 'UNKNOWN',
				        'UNKNOWN', 60, 'CLASS', 0, now(), now(), true)
				RETURNING id
				""", Long.class, "TL" + (System.nanoTime() % 1_000_000_000_000L), classes.get("class1_code"),
				classes.get("class2_code"), classes.get("class3_code"), name, lng, lat);
		if (withPhoto) {
			jdbc.update("UPDATE places SET image_url = ?, thumbnail_url = ?, image_license = 'Type1' WHERE id = ?",
					"https://example.com/" + placeId, "https://example.com/thumb/" + placeId, placeId);
		}
		return placeId;
	}

	private long insertDay(long tripId, int dayNumber, String date) {
		return jdbc.queryForObject("INSERT INTO trip_days (trip_id, day_number, trip_date) VALUES (?, ?, ?::date) RETURNING id",
				Long.class, tripId, dayNumber, date);
	}

	private long insertItem(long dayId, int seq, long placeId, String mealType) {
		return jdbc.queryForObject("""
				INSERT INTO trip_items (trip_day_id, seq, place_id, origin, meal_type, arrive_time, depart_time, stay_minutes)
				VALUES (?, ?, ?, 'SELECTED', ?, '00:00', '00:00', 60) RETURNING id
				""", Long.class, dayId, seq, placeId, mealType);
	}

	/** 테스트의 요청들은 한 트랜잭션에서 돌므로, 요청이 끝난 것처럼 DB에 쓰고 영속성 컨텍스트를 비운다 */
	private void flush() {
		entityManager.flush();
		entityManager.clear();
	}

	// --- 응답 읽기

	private static Object read(MvcTestResult result, String path) {
		try {
			return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Long> ids(MvcTestResult result, String path) {
		return ((List<Number>) read(result, path)).stream().map(Number::longValue).toList();
	}
}
