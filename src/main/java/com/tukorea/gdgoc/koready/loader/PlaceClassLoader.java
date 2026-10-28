package com.tukorea.gdgoc.koready.loader;

import static com.tukorea.gdgoc.koready.loader.TourApiFiles.text;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * {@code place_classes}: TourAPI 신분류 코드표. 응답 1행(소분류)에서 대·중·소 코드를 모두 만든다.
 */
@Slf4j
@Component
@Profile("loader")
@RequiredArgsConstructor
class PlaceClassLoader {

	/** 추천 후보·장소 검색에서 뺄 분류 (사후면세점) */
	private static final Set<String> EXCLUDED = Set.of("SH04");

	private static final String UPSERT = """
			INSERT INTO place_classes (code, level, parent_code, name_ko, excluded)
			VALUES (?, ?, ?, ?, ?)
			ON CONFLICT (code) DO UPDATE SET
				level = EXCLUDED.level,
				parent_code = EXCLUDED.parent_code,
				name_ko = EXCLUDED.name_ko,
				excluded = EXCLUDED.excluded
			""";
	private static final int[] TYPES = {Types.VARCHAR, Types.SMALLINT, Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN};

	private final TourApiFiles files;
	private final JdbcTemplate jdbc;

	@Transactional
	public void load() {
		Map<String, Object[]> rows = new LinkedHashMap<>();
		for (JsonNode item : files.classCodeItems()) {
			String code1 = text(item, "lclsSystm1Cd");
			String code2 = text(item, "lclsSystm2Cd");
			String code3 = text(item, "lclsSystm3Cd");
			rows.putIfAbsent(code1, row(code1, 1, null, text(item, "lclsSystm1Nm")));
			rows.putIfAbsent(code2, row(code2, 2, code1, text(item, "lclsSystm2Nm")));
			rows.putIfAbsent(code3, row(code3, 3, code2, text(item, "lclsSystm3Nm")));
		}
		// 부모 코드가 먼저 들어가도록 대 → 중 → 소 순서로
		List<Object[]> sorted = new ArrayList<>(rows.values());
		sorted.sort(Comparator.comparingInt(r -> (Short) r[1]));
		jdbc.batchUpdate(UPSERT, sorted, TYPES);
		log.info("place_classes {}행", sorted.size());
	}

	private static Object[] row(String code, int level, String parentCode, String nameKo) {
		return new Object[] {code, (short) level, parentCode, nameKo, EXCLUDED.contains(code)};
	}
}
