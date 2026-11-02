package com.tukorea.gdgoc.koready.domain.place.repository;

import com.tukorea.gdgoc.koready.domain.place.entity.Place;
import com.tukorea.gdgoc.koready.global.util.SearchText;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 숙소 검색 SQL. 숙박(콘텐츠 유형 32) 중 이름(국문·영문)이나 주소에 검색어가 들어간 곳을 찾고, 같은 조건으로 개수를 센다.
 */
@Repository
@RequiredArgsConstructor
public class LodgingRepository {

	/**
	 * 사라진 장소(여행 숙소로 고를 수 없음)와 제외 분류를 뺀다. 주소는 응답 주소처럼 addr1·addr2를 이어 붙여 찾는다
	 */
	private static final String MATCHES = """
			p.content_type_id = :contentTypeId
			    AND p.active
			    AND %s
			    AND (p.name_ko ILIKE :pattern OR p.name_en ILIKE :pattern OR concat_ws(' ', p.addr1, p.addr2) ILIKE :pattern)
			""".formatted(PlaceSql.NOT_EXCLUDED);

	/**
	 * 이름이 정확히 맞는 것 → 이름에 들어간 것 → 주소에만 들어간 것 순이고, 그 안은 국문명 가나다순이다.
	 * DB 기본 정렬(en_US.utf8)은 한글을 가나다순으로 두지 않아 코드 포인트 순(COLLATE "C")으로 정렬한다
	 */
	private static final String SEARCHED = """
			SELECT p.id, p.name_ko, p.name_en, p.addr1, p.addr2,
			       p.class3_code, c3.name_ko AS class3_name_ko, c3.name_en AS class3_name_en,
			       ST_Y(p.location::geometry) AS lat, ST_X(p.location::geometry) AS lng
			FROM places p
			JOIN place_classes c3 ON c3.code = p.class3_code
			WHERE %s
			ORDER BY CASE WHEN lower(p.name_ko) = lower(:query) OR lower(p.name_en) = lower(:query) THEN 0
			              WHEN p.name_ko ILIKE :pattern OR p.name_en ILIKE :pattern THEN 1
			              ELSE 2 END,
			         p.name_ko COLLATE "C", p.id
			LIMIT :limit OFFSET :offset
			""".formatted(MATCHES);

	private static final String COUNT = "SELECT count(*) FROM places p WHERE " + MATCHES;

	private final NamedParameterJdbcTemplate jdbc;

	public List<LodgingRow> find(String query, long offset, int limit) {
		MapSqlParameterSource params = params(query)
				.addValue("offset", offset)
				.addValue("limit", limit);
		return jdbc.query(SEARCHED, params, LodgingRepository::row);
	}

	public long count(String query) {
		Long count = jdbc.queryForObject(COUNT, params(query), Long.class);
		return count == null ? 0 : count;
	}

	private static MapSqlParameterSource params(String query) {
		return new MapSqlParameterSource()
				.addValue("contentTypeId", Place.LODGING_CONTENT_TYPE_ID)
				.addValue("query", query)
				.addValue("pattern", SearchText.containsPattern(query));
	}

	private static LodgingRow row(ResultSet rs, int rowNum) throws SQLException {
		return new LodgingRow(
				rs.getLong("id"),
				rs.getString("name_ko"),
				rs.getString("name_en"),
				rs.getString("addr1"),
				rs.getString("addr2"),
				rs.getString("class3_code"),
				rs.getString("class3_name_ko"),
				rs.getString("class3_name_en"),
				rs.getDouble("lat"),
				rs.getDouble("lng"));
	}

	/** 숙소 한 행 */
	public record LodgingRow(long placeId, String nameKo, String nameEn, String addr1, String addr2, String class3Code,
			String class3NameKo, String class3NameEn, double lat, double lng) {
	}
}
