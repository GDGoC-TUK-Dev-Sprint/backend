package com.tukorea.gdgoc.koready.domain.geo.repository;

import com.tukorea.gdgoc.koready.domain.place.repository.PlaceSql;
import com.tukorea.gdgoc.koready.global.util.SearchText;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 주소·장소명 검색의 장소명 SQL. 장소(숙박 포함) 중 이름(국문·영문)에 검색어가 들어간 곳을 찾는다.
 */
@Repository
@RequiredArgsConstructor
public class GeoPlaceRepository {

	/**
	 * 사라진 장소와 제외 분류를 뺀다. 이름이 정확히 맞는 것 → 이름이 검색어로 시작하는 것 → 그 밖에 이름에 들어간 것 순이고,
	 * 그 안은 인기도 신호가 많은 순 → 국문명 가나다순(COLLATE "C")이다
	 */
	private static final String SEARCHED = """
			SELECT p.name_ko, p.name_en, p.addr1, p.addr2, p.ldong_regn_cd,
			       ST_Y(p.location::geometry) AS lat, ST_X(p.location::geometry) AS lng
			FROM places p
			WHERE p.active
			  AND %s
			  AND (p.name_ko ILIKE :pattern OR p.name_en ILIKE :pattern)
			ORDER BY CASE WHEN lower(p.name_ko) = lower(:query) OR lower(p.name_en) = lower(:query) THEN 0
			              WHEN p.name_ko ILIKE :prefix OR p.name_en ILIKE :prefix THEN 1
			              ELSE 2 END,
			         p.signal_count DESC, p.name_ko COLLATE "C", p.id
			LIMIT :limit
			""".formatted(PlaceSql.NOT_EXCLUDED);

	private final NamedParameterJdbcTemplate jdbc;

	public List<GeoPlaceRow> findByName(String query, int limit) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("query", query)
				.addValue("pattern", SearchText.containsPattern(query))
				.addValue("prefix", SearchText.prefixPattern(query))
				.addValue("limit", limit);
		return jdbc.query(SEARCHED, params, GeoPlaceRepository::row);
	}

	private static GeoPlaceRow row(ResultSet rs, int rowNum) throws SQLException {
		return new GeoPlaceRow(
				rs.getString("name_ko"),
				rs.getString("name_en"),
				rs.getString("addr1"),
				rs.getString("addr2"),
				rs.getString("ldong_regn_cd"),
				rs.getDouble("lat"),
				rs.getDouble("lng"));
	}

	/**
	 * 장소 한 행.
	 *
	 * @param regionCode 법정동 시도 코드 (서울 11)
	 */
	public record GeoPlaceRow(String nameKo, String nameEn, String addr1, String addr2,
			String regionCode, double lat, double lng) {
	}
}
