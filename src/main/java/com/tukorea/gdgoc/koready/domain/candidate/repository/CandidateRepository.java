package com.tukorea.gdgoc.koready.domain.candidate.repository;

import com.tukorea.gdgoc.koready.global.util.SearchText;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 장소 후보 SQL. 범위 거르기 → 점수 → 순위 → 페이지를 쿼리 한 번으로 하고, 같은 범위로 개수를 센다.
 * 순위식은 {@link #SCORED}(점수)와 {@link #RANKED}(카테고리별 순위를 번갈아 둠)에만 있다. 결과는 저장하지 않는다.
 * 사진·분류 이름·신호·담김은 페이지를 자른 뒤 그 행에만 붙인다.
 */
@Repository
@RequiredArgsConstructor
public class CandidateRepository {

	/** 후보 범위: 사라진 장소, 숙박, 제외 분류, 여행 기간과 겹치지 않는 축제·행사를 뺀다 */
	private static final String SCOPE = """
			p.active
			    AND p.class1_code <> 'AC'
			    AND NOT EXISTS (SELECT 1 FROM place_classes x
			                    WHERE x.excluded AND x.code IN (p.class1_code, p.class2_code, p.class3_code))
			    AND (p.event_end_date IS NULL OR p.event_end_date >= :startDate)
			    AND (p.event_start_date IS NULL OR p.event_start_date <= :endDate)
			""";
	private static final String IN_CATEGORIES = SCOPE + "    AND p.class1_code IN (:categories)\n";
	private static final String NAME_MATCHES = SCOPE + "    AND (p.name_ko ILIKE :pattern OR p.name_en ILIKE :pattern)\n";

	/** 점수 = 신호 개수 × 신호 1개의 거리(설정값) − 숙소 직선거리(m). %s는 범위 조건 */
	private static final String SCORED = """
			WITH stay AS (SELECT stay_location AS location FROM trips WHERE id = :tripId),
			measured AS (
			  SELECT p.id, p.class1_code, p.signal_count, p.name_ko, p.name_en,
			         ST_Distance(p.location, stay.location) AS distance_m
			  FROM places p CROSS JOIN stay
			  WHERE %s
			),
			scored AS (
			  SELECT measured.*, signal_count * :signalWeightM - distance_m AS score FROM measured
			)
			""";

	private static final String COLUMNS = """
			SELECT p.id, p.name_ko, p.name_en,
			       p.class1_code, c1.name_ko AS class1_name_ko, c1.name_en AS class1_name_en,
			       p.class3_code, c3.name_ko AS class3_name_ko, c3.name_en AS class3_name_en,
			       p.image_url, p.thumbnail_url, p.image_license,
			       ST_Y(p.location::geometry) AS lat, ST_X(p.location::geometry) AS lng,
			       r.distance_m,
			       ARRAY(SELECT s.signal_type FROM place_signals s WHERE s.place_id = p.id) AS signals,
			       EXISTS (SELECT 1 FROM trip_items i JOIN trip_days d ON d.id = i.trip_day_id
			               WHERE d.trip_id = :tripId AND i.place_id = p.id AND i.origin = 'SELECTED') AS in_trip,
			""";
	private static final String JOINS = """
			JOIN places p ON p.id = r.id
			JOIN place_classes c1 ON c1.code = p.class1_code
			JOIN place_classes c3 ON c3.code = p.class3_code
			""";

	/**
	 * 추천 목록: 카테고리마다 점수순 순위를 매기고, 1위끼리·2위끼리 묶어 묶음 안은 점수순으로 둔다.
	 * 신호가 적은 카테고리(음식)가 신호가 많은 카테고리(역사관광) 뒤로 묻히지 않는다. 같은 점수면 가까운 순 → 장소 ID 순
	 */
	private static final String RANKED = SCORED.formatted(IN_CATEGORIES) + """
			, category_ranked AS (
			  SELECT scored.*,
			         row_number() OVER (PARTITION BY class1_code ORDER BY score DESC, distance_m, id) AS category_rank
			  FROM scored
			),
			ranked AS (
			  SELECT id, distance_m, row_number() OVER (ORDER BY category_rank, score DESC, distance_m, id) AS rank
			  FROM category_ranked
			),
			page AS (SELECT id, distance_m, rank FROM ranked ORDER BY rank LIMIT :limit OFFSET :offset)
			""" + COLUMNS + """
			       r.rank
			FROM page r
			""" + JOINS + """
			ORDER BY r.rank
			""";

	/** 이름 검색: 이름이 정확히 맞는 것 먼저, 그 안은 추천 점수순. 순위는 없다 */
	private static final String SEARCHED = SCORED.formatted(NAME_MATCHES) + """
			, matched AS (
			  SELECT id, distance_m, score,
			         CASE WHEN lower(name_ko) = lower(:query) OR lower(name_en) = lower(:query) THEN 0 ELSE 1 END AS name_order
			  FROM scored
			),
			page AS (
			  SELECT id, distance_m, name_order, score FROM matched
			  ORDER BY name_order, score DESC, distance_m, id LIMIT :limit OFFSET :offset
			)
			""" + COLUMNS + """
			       NULL::bigint AS rank
			FROM page r
			""" + JOINS + """
			ORDER BY r.name_order, r.score DESC, r.distance_m, r.id
			""";

	private static final String COUNT_RANKED = "SELECT count(*) FROM places p WHERE " + IN_CATEGORIES;
	private static final String COUNT_SEARCHED = "SELECT count(*) FROM places p WHERE " + NAME_MATCHES;

	private final NamedParameterJdbcTemplate jdbc;

	public List<CandidateRow> find(Criteria criteria, long offset, int limit) {
		MapSqlParameterSource params = criteria.params()
				.addValue("offset", offset)
				.addValue("limit", limit);
		return jdbc.query(criteria.isSearch() ? SEARCHED : RANKED, params, CandidateRepository::row);
	}

	public long count(Criteria criteria) {
		Long count = jdbc.queryForObject(criteria.isSearch() ? COUNT_SEARCHED : COUNT_RANKED, criteria.params(), Long.class);
		return count == null ? 0 : count;
	}

	private static CandidateRow row(ResultSet rs, int rowNum) throws SQLException {
		return new CandidateRow(
				rs.getLong("id"),
				rs.getString("name_ko"),
				rs.getString("name_en"),
				rs.getString("class1_code"),
				rs.getString("class1_name_ko"),
				rs.getString("class1_name_en"),
				rs.getString("class3_code"),
				rs.getString("class3_name_ko"),
				rs.getString("class3_name_en"),
				rs.getString("image_url"),
				rs.getString("thumbnail_url"),
				rs.getString("image_license"),
				rs.getDouble("lat"),
				rs.getDouble("lng"),
				rs.getDouble("distance_m"),
				List.of((String[]) rs.getArray("signals").getArray()),
				rs.getBoolean("in_trip"),
				rs.getObject("rank", Long.class));
	}

	/**
	 * 후보를 찾는 조건.
	 *
	 * @param categories 추천 목록의 카테고리(대분류 코드). 검색이면 쓰지 않는다
	 * @param query 검색어. null이면 추천 목록
	 * @param signalWeightM 인기도 신호 1개가 맞먹는 숙소 거리(m)
	 */
	public record Criteria(long tripId, LocalDate startDate, LocalDate endDate, Collection<String> categories, String query,
			int signalWeightM) {

		boolean isSearch() {
			return query != null;
		}

		MapSqlParameterSource params() {
			MapSqlParameterSource params = new MapSqlParameterSource()
					.addValue("tripId", tripId)
					.addValue("startDate", startDate)
					.addValue("endDate", endDate)
					.addValue("signalWeightM", signalWeightM);
			if (isSearch()) {
				params.addValue("query", query).addValue("pattern", SearchText.containsPattern(query));
			} else {
				params.addValue("categories", categories);
			}
			return params;
		}
	}

	/** 후보 한 행. 순위는 검색이면 null */
	public record CandidateRow(long placeId, String nameKo, String nameEn, String class1Code, String class1NameKo,
			String class1NameEn, String class3Code, String class3NameKo, String class3NameEn, String imageUrl,
			String thumbnailUrl, String imageLicense, double lat, double lng, double distanceM, List<String> signals,
			boolean inTrip, Long rank) {
	}
}
