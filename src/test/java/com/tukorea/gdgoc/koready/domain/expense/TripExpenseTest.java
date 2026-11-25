package com.tukorea.gdgoc.koready.domain.expense;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
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
 * 여행 후 실제 지출: 지출 입력 화면(28)·지출 저장(29)·지출 리포트(30). "오늘"은 2026-11-01이다
 * ({@link com.tukorea.gdgoc.koready.support.TestClockConfig}). 여행은 10/20~10/22 2인 확정 여행이다.
 */
@IntegrationTest
class TripExpenseTest {

	/** 1인 기준: 식비 40,000 · 쇼핑 70,000 · 기타 2개 12,000 + 3,000 (관광·교통은 입력하지 않음) */
	private static final String PER_PERSON_EXPENSES = """
			{"basis": "PER_PERSON", "items": [
			  {"item": "FOOD", "amount": {"amount": 40000, "currency": "KRW"}},
			  {"item": "SHOPPING", "amount": {"amount": 70000, "currency": "KRW"}},
			  {"item": "OTHER", "label": " 면세점 ", "amount": {"amount": 12000, "currency": "KRW"}},
			  {"item": "OTHER", "label": "기념품", "amount": {"amount": 3000, "currency": "KRW"}}]}
			""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	long userId;
	long tripId;

	@BeforeEach
	void createCompletedTrip() {
		userId = insertUser();
		tripId = insertTrip(userId, "CONFIRMED", "2026-10-20", "2026-10-22");
		// 확정 때 저장한 항목별 1인 예상 (원)
		insertEstimate("FOOD", 30_000, 50_000);
		insertEstimate("SIGHTSEEING", 10_000, 10_000);
		insertEstimate("TRANSPORT", 5_000, 5_000);
		insertEstimate("SHOPPING", 20_000, 60_000);
		insertEstimate("OTHER", 0, 0);
	}

	@Test
	void sheetBeforeSaveHasEstimatesAndBudget() {
		assertThat(getSheet())
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{"trip": {"tripId": %d, "status": "CONFIRMED", "progress": "COMPLETED", "partySize": 2},
						 "estimates": [
						   {"item": "FOOD", "perPerson": {"min": 30000, "max": 50000, "currency": "KRW"},
						    "total": {"min": 60000, "max": 100000, "currency": "KRW"}},
						   {"item": "SIGHTSEEING", "perPerson": {"min": 10000, "max": 10000}},
						   {"item": "TRANSPORT", "perPerson": {"min": 5000, "max": 5000}},
						   {"item": "SHOPPING", "perPerson": {"min": 20000, "max": 60000}},
						   {"item": "OTHER", "perPerson": {"min": 0, "max": 0}}],
						 "estimateTotal": {"perPerson": {"min": 65000, "max": 125000},
						                   "total": {"min": 130000, "max": 250000}},
						 "budget": {"perPerson": {"amount": 300000, "currency": "KRW"},
						            "total": {"amount": 600000, "currency": "KRW"}},
						 "expenses": [],
						 "saved": false}
						""".formatted(tripId));
	}

	@Test
	void savePerPersonStoresTotalInSentOrder() {
		assertThat(save(PER_PERSON_EXPENSES))
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{"saved": true, "expenses": [
						  {"item": "FOOD", "label": null, "perPerson": {"amount": 40000}, "total": {"amount": 80000}},
						  {"item": "SHOPPING", "perPerson": {"amount": 70000}, "total": {"amount": 140000}},
						  {"item": "OTHER", "label": "면세점", "perPerson": {"amount": 12000}, "total": {"amount": 24000}},
						  {"item": "OTHER", "label": "기념품", "perPerson": {"amount": 3000}, "total": {"amount": 6000}}]}
						""");
		flushAndClear();

		assertThat(jdbc.queryForList(
				"SELECT item, label, amount_krw FROM trip_expenses WHERE trip_id = ? ORDER BY seq", tripId))
				.containsExactly(
						row("FOOD", null, 80_000),
						row("SHOPPING", null, 140_000),
						row("OTHER", "면세점", 24_000),
						row("OTHER", "기념품", 6_000));
		assertThat(getSheet()).hasStatusOk().bodyJson().extractingPath("$.expenses.length()").isEqualTo(4);
	}

	@Test
	void saveOverwritesPreviousExpenses() {
		save(PER_PERSON_EXPENSES);
		flushAndClear();

		assertThat(save("""
				{"basis": "TOTAL", "items": [{"item": "TRANSPORT", "amount": {"amount": 9001, "currency": "KRW"}}]}
				"""))
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{"expenses": [{"item": "TRANSPORT", "perPerson": {"amount": 4501}, "total": {"amount": 9001}}]}
						""");
		flushAndClear();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_expenses WHERE trip_id = ?", Integer.class, tripId))
				.isEqualTo(1);
	}

	@Test
	void invalidItemsAreAllReportedAndNothingChanges() {
		save(PER_PERSON_EXPENSES);
		flushAndClear();

		assertThat(save("""
				{"basis": "TOTAL", "items": [
				  {"item": "FOOD", "amount": {"amount": 1000, "currency": "KRW"}},
				  {"item": "FOOD", "amount": {"amount": 1000, "currency": "KRW"}},
				  {"item": "OTHER", "label": "  ", "amount": {"amount": 1000, "currency": "KRW"}},
				  {"item": "SHOPPING", "amount": {"amount": 1.234, "currency": "USD"}}]}
				"""))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [
						  {"field": "items[1].item"},
						  {"field": "items[2].label", "reason": "기타 항목은 이름이 필요합니다"},
						  {"field": "items[3].amount.amount", "reason": "소수 둘째 자리까지여야 합니다"}]}
						""");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_expenses WHERE trip_id = ?", Integer.class, tripId))
				.isEqualTo(4);
	}

	@Test
	void emptyItemsAreRejected() {
		assertThat(save("{\"basis\": \"TOTAL\", \"items\": []}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "items", "reason": "1개 이상이어야 합니다"}]}
						""");
	}

	@Test
	void reportComparesPerPersonAndTotal() {
		save(PER_PERSON_EXPENSES);
		flushAndClear();

		// 1인 실제 125,000원, 예상 65,000~125,000원 (기준값 95,000원)
		assertThat(getReport())
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{"trip": {"tripId": %d},
						 "perPerson": {
						   "estimate": {"min": 65000, "max": 125000, "currency": "KRW"},
						   "actual": {"amount": 125000, "currency": "KRW"},
						   "status": "WITHIN",
						   "difference": {"amount": 30000},
						   "differencePct": 31.6, "accuracyPct": 68.4, "errorRatePct": 31.6, "executionPct": 131.6,
						   "items": [
						     {"item": "FOOD", "labels": [], "estimate": {"min": 30000, "max": 50000},
						      "actual": {"amount": 40000}, "status": "WITHIN", "difference": {"amount": 0}, "sharePct": 32.0},
						     {"item": "SIGHTSEEING", "actual": null, "status": null, "difference": null, "sharePct": null},
						     {"item": "TRANSPORT", "actual": null},
						     {"item": "SHOPPING", "actual": {"amount": 70000}, "status": "ABOVE",
						      "difference": {"amount": 30000}, "sharePct": 56.0},
						     {"item": "OTHER", "labels": ["면세점", "기념품"], "estimate": {"min": 0, "max": 0},
						      "actual": {"amount": 15000}, "status": "ABOVE", "difference": {"amount": 15000}, "sharePct": 12.0}]},
						 "total": {
						   "estimate": {"min": 130000, "max": 250000},
						   "actual": {"amount": 250000},
						   "status": "WITHIN",
						   "difference": {"amount": 60000}},
						 "insight": "쇼핑에서 예상보다 1인 30,000 KRW 더 썼어요."}
						""".formatted(tripId));
	}

	@Test
	void reportNeedsSavedExpenses() {
		assertThat(getReport())
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("EXPENSES_NOT_SAVED");
	}

	@Test
	void onlyCompletedConfirmedTripsAreAccepted() {
		long ongoing = insertTrip(userId, "CONFIRMED", "2026-10-30", "2026-11-01");
		long draft = insertTrip(userId, "DRAFT", "2026-10-20", "2026-10-22");

		for (long id : List.of(ongoing, draft)) {
			for (MvcTestResult result : List.of(getSheet(id), save(id, PER_PERSON_EXPENSES), getReport(id))) {
				assertThat(result)
						.hasStatus(HttpStatus.CONFLICT)
						.bodyJson()
						.extractingPath("$.code").isEqualTo("TRIP_NOT_COMPLETED");
			}
		}
	}

	@Test
	void othersTripIsNotFound() {
		long otherUserId = insertUser();

		assertThat(mvc.get().uri("/api/trips/{tripId}/expenses", tripId).header("X-Dev-User-Id", otherUserId))
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("TRIP_NOT_FOUND");
	}

	private long insertUser() {
		return jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}

	private long insertTrip(long ownerId, String status, String startDate, String endDate) {
		return jdbc.queryForObject("""
				INSERT INTO trips (user_id, status, nationality_code, start_date, end_date, party_size,
				                   budget_per_person_krw, stay_type, stay_area_code, stay_name, stay_location,
				                   confirmed_at, created_at, updated_at)
				VALUES (?, ?, 'JP', ?::date, ?::date, 2, 300000, 'AREA', 'MYEONGDONG', '명동',
				        ST_GeogFromText('POINT(126.9850 37.5636)'),
				        CASE WHEN ?::text = 'CONFIRMED' THEN now() END, now(), now())
				RETURNING id
				""", Long.class, ownerId, status, startDate, endDate, status);
	}

	private void insertEstimate(String item, int minKrw, int maxKrw) {
		jdbc.update("INSERT INTO trip_cost_estimates (trip_id, item, min_krw, max_krw, created_at) VALUES (?, ?, ?, ?, now())",
				tripId, item, minKrw, maxKrw);
	}

	private static Map<String, Object> row(String item, String label, int amountKrw) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("item", item);
		row.put("label", label);
		row.put("amount_krw", amountKrw);
		return row;
	}

	/** 테스트의 요청들은 한 트랜잭션에서 돌므로, 요청이 끝난 것처럼 DB에 쓰고 영속성 컨텍스트를 비운다 */
	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	private MvcTestResult getSheet() {
		return getSheet(tripId);
	}

	private MvcTestResult getSheet(long id) {
		return mvc.get().uri("/api/trips/{tripId}/expenses", id).header("X-Dev-User-Id", userId).exchange();
	}

	private MvcTestResult save(String body) {
		return save(tripId, body);
	}

	private MvcTestResult save(long id, String body) {
		return mvc.put().uri("/api/trips/{tripId}/expenses", id)
				.header("X-Dev-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.exchange();
	}

	private MvcTestResult getReport() {
		return getReport(tripId);
	}

	private MvcTestResult getReport(long id) {
		return mvc.get().uri("/api/trips/{tripId}/expense-report", id).header("X-Dev-User-Id", userId).exchange();
	}
}
