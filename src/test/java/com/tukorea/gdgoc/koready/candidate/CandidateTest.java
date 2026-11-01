package com.tukorea.gdgoc.koready.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 장소 후보. 여행은 명동(지역 중심), 2026-11-10~12(3일)이고 순위는 덤프 데이터 기준이다.
 */
@IntegrationTest
class CandidateTest {

	private static final String MYEONGDONG_TRIP = """
			{"nationality": "JP", "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 2,
			 "categories": %s, "stay": {"type": "AREA", "areaCode": "MYEONGDONG"}}
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

	/** "전체"는 카테고리마다 1위끼리, 2위끼리 번갈아 둔다. 한 줄 순위면 음식이 22위에야 나온다 */
	@Test
	void allFilterTakesTurnsAcrossChosenCategories() {
		long tripId = createTrip("[\"HS\", \"SH\", \"FD\"]");

		MvcTestResult result = candidates(tripId);

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"guide": {"days": 3, "recommendedMin": 9, "recommendedMax": 15, "maxPerDay": 6},
				 "page": 0, "size": 5, "totalCount": 2077, "hasNext": true}
				""");
		assertThat(result).bodyJson().extractingPath("$.items[*].place.nameKo")
				.isEqualTo(List.of("덕수궁", "광장시장", "남산산채집", "경복궁", "롯데백화점 본점"));
		assertThat(result).bodyJson().extractingPath("$.items[*].rank").isEqualTo(List.of(1, 2, 3, 4, 5));
	}

	@Test
	void categoryFilterRanksWithinCategoryAcrossPages() {
		long tripId = createTrip("[\"HS\", \"SH\", \"FD\"]");

		assertThat(candidates(tripId, "category", "HS")).bodyJson().extractingPath("$.items[*].place.nameKo")
				.isEqualTo(List.of("덕수궁", "경복궁", "창경궁", "남산골한옥마을", "숭례문"));
		MvcTestResult secondPage = candidates(tripId, "category", "HS", "page", "1");
		assertThat(secondPage).bodyJson().extractingPath("$.items[*].rank").isEqualTo(List.of(6, 7, 8, 9, 10));
		assertThat(secondPage).bodyJson().extractingPath("$.items[0].place.nameKo").isEqualTo("종묘 [유네스코 세계유산]");
		assertThat(secondPage).bodyJson().extractingPath("$.totalCount").isEqualTo(227);
	}

	@Test
	void itemShowsPlaceDistanceReasonsAndSignals() {
		long tripId = createTrip("[\"HS\"]");
		long distance = jdbc.queryForObject("""
				SELECT round(ST_Distance(p.location, t.stay_location)) FROM places p, trips t
				WHERE p.name_ko = '덕수궁' AND t.id = ?
				""", Long.class, tripId);

		MvcTestResult result = candidates(tripId, "size", "2");

		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{"items": [
				  {"rank": 1,
				   "place": {"name": "덕수궁", "nameKo": "덕수궁", "category": {"code": "HS", "name": "역사관광"},
				             "classification": {"code": "HS010100"}},
				   "distanceFromStayM": %d,
				   "reasons": ["CATEGORY_MATCH", "NEAR_STAY"],
				   "signals": ["COURSE", "FOREIGN_VISITORS", "ENG_SERVICE", "VISIT_SEOUL", "HUB_TOUR"],
				   "inTrip": false},
				  {"rank": 2, "place": {"nameKo": "경복궁"}, "reasons": ["CATEGORY_MATCH"]}]}
				""".formatted(distance));
		assertThat(result).bodyJson().extractingPath("$.items[0].place.image.license").asString().matches("TYPE[13]");
		assertThat(result).bodyJson().extractingPath("$.items[0].place.location.lat").isNotNull();
	}

	/** 축제·행사는 여행 기간과 겹치는 것만 남고, 날짜가 비어 있는 행사는 기간과 상관없이 남는다 */
	@Test
	void eventsMustOverlapTripDates() {
		long tripId = createTrip("[\"EV\"]");
		long laterEventId = jdbc.queryForObject("""
				SELECT id FROM places WHERE class1_code = 'EV' AND event_start_date > '2026-11-12'
				ORDER BY event_start_date, id LIMIT 1
				""", Long.class);
		long undatedEventId = jdbc.queryForObject("""
				SELECT id FROM places WHERE class1_code = 'EV' AND event_start_date IS NULL AND event_end_date IS NULL
				ORDER BY id LIMIT 1
				""", Long.class);

		assertThat(candidates(tripId, "size", "50")).bodyJson().extractingPath("$.items[*].place.placeId").asArray()
				.contains((int) undatedEventId).doesNotContain((int) laterEventId);
		MvcTestResult lastPage = candidates(tripId, "page", "2");
		assertThat(lastPage).bodyJson().isLenientlyEqualTo("""
				{"page": 2, "size": 5, "totalCount": 12, "hasNext": false}
				""");
		assertThat(lastPage).bodyJson().extractingPath("$.items[*].rank").isEqualTo(List.of(11, 12));
	}

	/** TourAPI에서 사라진 장소(active = false)는 빠진다 */
	@Test
	void leavesOutInactivePlaces() {
		long tripId = createTrip("[\"HS\"]");
		jdbc.update("UPDATE places SET active = false WHERE name_ko = '덕수궁'");

		MvcTestResult result = candidates(tripId);

		assertThat(result).bodyJson().extractingPath("$.items[*].place.nameKo").asArray().doesNotContain("덕수궁");
		assertThat(result).bodyJson().extractingPath("$.totalCount").isEqualTo(226);
	}

	@Test
	void placeWithoutPhotoHasNullImage() {
		long tripId = createTrip("[\"HS\"]");

		assertThat(candidates(tripId, "query", "몽촌토성")).bodyJson().isLenientlyEqualTo("""
				{"totalCount": 1, "items": [{"place": {"nameKo": "몽촌토성", "image": null}}]}
				""");
	}

	/** 카테고리를 하나도 고르지 않았으면 8개 전체이고 필터도 8개 중에서 고른다. 테마가 맞는 장소는 없다 */
	@Test
	void noChosenCategoryMeansAllEight() {
		long tripId = createTrip("[]");

		MvcTestResult result = candidates(tripId, "size", "8");

		assertThat(result).bodyJson().extractingPath("$.items[*].place.category.code").asArray()
				.containsExactlyInAnyOrder("SH", "FD", "VE", "HS", "EV", "EX", "NA", "LS");
		assertThat(result).bodyJson().extractingPath("$.items[*].reasons[*]").asArray()
				.doesNotContain("CATEGORY_MATCH");
		assertThat(candidates(tripId, "category", "FD")).hasStatusOk().bodyJson()
				.extractingPath("$.items[*].place.category.code").asArray().containsOnly("FD");
	}

	@Test
	void filterMustBeChosenCategory() {
		long tripId = createTrip("[\"HS\", \"SH\"]");

		assertThat(candidates(tripId, "category", "FD"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "category", "reason": "이 여행에서 고른 카테고리가 아닙니다"}]}
						""");
		assertThat(candidates(tripId, "category", "AC"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "category", "reason": "형식이 맞지 않습니다"}]}
						""");
	}

	/** 검색은 카테고리 조건을 풀고 이름으로 찾는다. 이름이 정확히 맞는 것이 먼저이고 순위는 없다 */
	@Test
	void searchIgnoresCategoriesAndPutsExactNameFirst() {
		long tripId = createTrip("[\"SH\"]");

		MvcTestResult result = candidates(tripId, "query", " 경복궁 ", "category", "SH");

		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.items[0].place.nameKo").isEqualTo("경복궁");
		assertThat(result).bodyJson().extractingPath("$.items[*].rank").asArray().containsOnlyNulls();
		assertThat(result).bodyJson().extractingPath("$.items[0].reasons").isEqualTo(List.of());
		// 사후면세점(다이소 경복궁역점 등)과 여행 전에 끝난 행사(경복궁 별빛야행 등)는 빠진다
		assertThat(result).bodyJson().extractingPath("$.items[*].place.nameKo").asArray()
				.containsExactlyInAnyOrder("경복궁", "경복궁에 맛있는 부엌", "한복남 경복궁점");
	}

	@Test
	void searchLeavesOutLodgingAndEndedEvents() {
		long tripId = createTrip("[\"EV\"]");
		long endedEventId = jdbc.queryForObject("""
				SELECT id FROM places WHERE class1_code = 'EV' AND event_end_date < '2026-11-10'
				ORDER BY event_end_date DESC, id LIMIT 1
				""", Long.class);
		String endedEventName = jdbc.queryForObject("SELECT name_ko FROM places WHERE id = ?", String.class, endedEventId);

		assertThat(candidates(tripId, "query", "호텔", "size", "50")).bodyJson()
				.extractingPath("$.items[*].place.category.code").asArray().isNotEmpty().doesNotContain("AC");
		assertThat(candidates(tripId, "query", endedEventName, "size", "50")).bodyJson()
				.extractingPath("$.items[*].place.placeId").asArray().doesNotContain((int) endedEventId);
	}

	/** 검색어의 %·_는 와일드카드가 아니라 글자 그대로 찾는다 (이름에 %가 들어간 장소는 없다) */
	@Test
	void searchTreatsWildcardsAsText() {
		long tripId = createTrip("[\"HS\"]");

		assertThat(candidates(tripId, "query", "%")).hasStatusOk().bodyJson()
				.extractingPath("$.totalCount").isEqualTo(0);
	}

	/** 검색어 길이는 앞뒤 공백을 뺀 글자 수로 센다. 아주 큰 페이지는 빈 목록이다 */
	@Test
	void acceptsPaddedQueryAndHugePage() {
		long tripId = createTrip("[\"HS\"]");

		assertThat(candidates(tripId, "query", " " + "가".repeat(50) + " ")).hasStatusOk();
		assertThat(candidates(tripId, "page", String.valueOf(Integer.MAX_VALUE))).hasStatusOk().bodyJson()
				.isLenientlyEqualTo("""
						{"items": [], "totalCount": 227, "hasNext": false}
						""");
	}

	@Test
	void rejectsBadQueryAndPage() {
		long tripId = createTrip("[\"HS\"]");

		// 전각 공백(U+3000)도 공백이다 (빈 검색어가 되어 모든 장소를 찾지 않는다)
		for (String blank : List.of("   ", "　")) {
			assertThat(candidates(tripId, "query", blank))
					.hasStatus(HttpStatus.BAD_REQUEST)
					.bodyJson()
					.isLenientlyEqualTo("""
							{"code": "INVALID_REQUEST", "fields": [{"field": "query", "reason": "공백만으로는 찾을 수 없습니다"}]}
							""");
		}
		for (String[] params : List.of(
				new String[] {"query", "가".repeat(51)},
				new String[] {"query", ""},
				new String[] {"size", "51"},
				new String[] {"page", "-1"},
				new String[] {"page", "abc"})) {
			assertThat(candidates(tripId, params))
					.hasStatus(HttpStatus.BAD_REQUEST)
					.bodyJson()
					.extractingPath("$.fields[0].field").isEqualTo(params[0]);
		}
	}

	/** 담긴 장소는 02c에서 담은 일정 항목만이다 (자동으로 채운 식사 자리는 아니다) */
	@Test
	void inTripMarksSelectedItemsOnly() {
		long tripId = createTrip("[\"HS\"]");
		long dayId = jdbc.queryForObject(
				"INSERT INTO trip_days (trip_id, day_number, trip_date) VALUES (?, 1, '2026-11-10') RETURNING id",
				Long.class, tripId);
		insertItem(dayId, 1, "덕수궁", "SELECTED");
		insertItem(dayId, 2, "경복궁", "MEAL");

		assertThat(candidates(tripId, "size", "2")).bodyJson().extractingPath("$.items[*].inTrip")
				.isEqualTo(List.of(true, false));
	}

	@Test
	void othersTripIsNotFound() {
		long tripId = createTrip("[\"HS\"]");
		long otherUserId = insertUser();

		assertThat(mvc.get().uri("/api/trips/{tripId}/candidates", tripId).header("X-Dev-User-Id", otherUserId))
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("TRIP_NOT_FOUND");
	}

	private long insertUser() {
		return jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}

	/** 여행을 만든다. 테스트의 요청들은 한 트랜잭션에서 돌므로, 요청이 끝난 것처럼 DB에 쓰고 영속성 컨텍스트를 비운다 */
	private long createTrip(String categories) {
		assertThat(mvc.post().uri("/api/trips")
				.header("X-Dev-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(MYEONGDONG_TRIP.formatted(categories)))
				.hasStatus(HttpStatus.CREATED);
		entityManager.flush();
		entityManager.clear();
		return jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, userId);
	}

	private void insertItem(long dayId, int seq, String placeName, String origin) {
		jdbc.update("""
				INSERT INTO trip_items (trip_day_id, seq, place_id, origin, meal_type, arrive_time, depart_time, stay_minutes)
				SELECT ?, ?, id, ?, CASE WHEN ? = 'MEAL' THEN 'LUNCH' END, '10:00', '11:00', 60
				FROM places WHERE name_ko = ?
				""", dayId, seq, origin, origin, placeName);
	}

	/** 장소 후보를 부른다. params는 이름·값을 번갈아 둔다 */
	private MvcTestResult candidates(long tripId, String... params) {
		var request = mvc.get().uri("/api/trips/{tripId}/candidates", tripId).header("X-Dev-User-Id", userId);
		for (int i = 0; i < params.length; i += 2) {
			request = request.param(params[i], params[i + 1]);
		}
		return request.exchange();
	}
}
