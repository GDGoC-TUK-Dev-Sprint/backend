package com.tukorea.gdgoc.koready.meta;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.common.LatLng;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class MetaOptionsTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void koreanOptions() {
		var body = assertThat(mvc.get().uri("/api/meta/options")).hasStatusOk().bodyJson();
		body.extractingPath("$.nationalities[*].code").asArray().containsExactly("CN", "JP", "US");
		body.extractingPath("$.nationalities[*].name").asArray().containsExactly("중국", "일본", "미국");
		body.extractingPath("$.categories[*].code").asArray()
				.containsExactly("SH", "FD", "VE", "HS", "EV", "EX", "NA", "LS");
		body.extractingPath("$.categories[*].name").asArray()
				.containsExactly("쇼핑", "음식", "문화관광", "역사관광", "축제/공연/행사", "체험관광", "자연관광", "레저스포츠");
		body.extractingPath("$.areas[*].code").asArray().containsExactly("MYEONGDONG", "JONGNO", "HONGDAE", "MAPO",
				"GANGNAM", "ITAEWON", "YONGSAN", "SEONGSU", "JAMSIL", "DONGDAEMUN", "YEOUIDO", "SEOUL_STATION",
				"NAMDAEMUN", "SINCHON");
		body.extractingPath("$.areas[*].name").asArray().containsExactly("명동", "종로", "홍대", "마포", "강남",
				"이태원", "용산", "성수", "잠실", "동대문", "여의도", "서울역", "남대문", "신촌");
		body.extractingPath("$.areas[0].center").isEqualTo(Map.of("lat", 37.562152093, "lng", 126.984915005));
	}

	@Test
	void englishNamesForCodeConstants() {
		var body = assertThat(mvc.get().uri("/api/meta/options").header("Accept-Language", "en"))
				.hasStatusOk().bodyJson();
		body.extractingPath("$.nationalities[*].name").asArray().containsExactly("China", "Japan", "United States");
		body.extractingPath("$.areas[11].name").isEqualTo("Seoul Station");
		body.extractingPath("$.categories.length()").isEqualTo(8);
	}

	@Test
	void areaCentersAreTourApiPlaceLocations() {
		for (Area area : Area.values()) {
			LatLng place = jdbc.queryForObject(
					"SELECT ST_Y(location::geometry), ST_X(location::geometry) FROM places WHERE content_id = ?",
					(rs, i) -> new LatLng(rs.getDouble(1), rs.getDouble(2)), area.getCenterContentId());
			assertThat(area.getCenter()).as(area.name()).isEqualTo(place);
		}
	}
}
