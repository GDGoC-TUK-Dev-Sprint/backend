package com.tukorea.gdgoc.koready.loader;

import static com.tukorea.gdgoc.koready.loader.TourApiFiles.text;

import com.tukorea.gdgoc.koready.place.SignalType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code place_signals}와 {@code places.signal_count}.
 * 여행코스 포함(COURSE)은 TourAPI 여행코스 반복정보의 구성 장소({@code subcontentid})로 바로 만든다.
 * 이름 매칭이 필요한 신호는 원본 폴더의 매칭 결과 파일({@code signals/*.json})을 읽는다. 다시 돌리면 지우고 다시 넣는다.
 */
@Slf4j
@Component
@Profile("loader")
class SignalLoader {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final String COURSE_TYPE = "25";
	private static final List<String> MATCHED_FILES = List.of("foreign-visitors", "eng-service", "visit-seoul", "hub-tour");
	private static final int NAME_MAX = 200;

	private static final String INSERT =
			"INSERT INTO place_signals (place_id, signal_type, value, base_period, matched_name) VALUES (?, ?, ?, ?, ?)";
	private static final int[] TYPES = {Types.BIGINT, Types.VARCHAR, Types.BIGINT, Types.VARCHAR, Types.VARCHAR};

	private final TourApiFiles files;
	private final JdbcTemplate jdbc;
	private final JsonMapper jsonMapper;
	private final Path rawDir;

	SignalLoader(TourApiFiles files, JdbcTemplate jdbc, JsonMapper jsonMapper, @Value("${koready.loader.raw-dir}") Path rawDir) {
		this.files = files;
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
		this.rawDir = rawDir;
	}

	@Transactional
	public void load() {
		Map<String, Long> placeIds = new HashMap<>();
		jdbc.query("SELECT content_id, id FROM places WHERE active", rs -> {
			placeIds.put(rs.getString(1), rs.getLong(2));
		});

		List<Object[]> rows = new ArrayList<>();
		Map<SignalType, Integer> counts = new EnumMap<>(SignalType.class);
		Map<SignalType, Integer> missing = new EnumMap<>(SignalType.class);

		// 여행코스: 장소마다 들어 있는 코스 이름 (값은 코스 수). 코스는 목록에 없고 상세 응답만 있다
		Map<String, Set<String>> courses = new TreeMap<>();
		for (String courseId : files.detailIds("info")) {
			List<JsonNode> parts = files.detailItems("info", courseId).stream()
					.filter(part -> COURSE_TYPE.equals(text(part, "contenttypeid")) && text(part, "subcontentid") != null)
					.toList();
			if (parts.isEmpty()) {
				continue;
			}
			List<JsonNode> common = files.detailItems("common", courseId);
			String title = common.isEmpty() || text(common.getFirst(), "title") == null ? courseId : text(common.getFirst(), "title");
			for (JsonNode part : parts) {
				courses.computeIfAbsent(text(part, "subcontentid"), k -> new LinkedHashSet<>()).add(title);
			}
		}
		String courseBasePeriod = collectedDate(rawDir.resolve("tourapi").resolve("kor").resolve("info"));
		courses.forEach((contentId, titles) -> add(rows, counts, missing, placeIds, SignalType.COURSE, contentId,
				(long) titles.size(), courseBasePeriod, String.join(" / ", titles)));

		for (String name : MATCHED_FILES) {
			JsonNode root = jsonMapper.readTree(rawDir.resolve("signals").resolve(name + ".json").toFile());
			SignalType type = SignalType.valueOf(root.path("signalType").asString());
			String basePeriod = root.path("basePeriod").asString();
			for (JsonNode item : root.path("items").values()) {
				JsonNode value = item.path("value");
				add(rows, counts, missing, placeIds, type, item.path("contentId").asString(),
						value.isNumber() ? value.asLong() : null, basePeriod, item.path("matchedName").asString(null));
			}
		}

		jdbc.update("DELETE FROM place_signals");
		jdbc.batchUpdate(INSERT, rows, TYPES);
		int counted = jdbc.update(
				"UPDATE places p SET signal_count = (SELECT count(*) FROM place_signals s WHERE s.place_id = p.id)");
		log.info("place_signals {}행 {} (적재하지 않은 장소라 뺌 {}), signal_count 다시 셈 {}곳", rows.size(), counts, missing, counted);
	}

	private static void add(List<Object[]> rows, Map<SignalType, Integer> counts, Map<SignalType, Integer> missing,
			Map<String, Long> placeIds, SignalType type, String contentId, Long value, String basePeriod, String matchedName) {
		Long placeId = placeIds.get(contentId);
		if (placeId == null) {
			missing.merge(type, 1, Integer::sum);
			return;
		}
		String name = matchedName == null || matchedName.length() <= NAME_MAX ? matchedName : matchedName.substring(0, NAME_MAX);
		rows.add(new Object[] {placeId, type.name(), value, basePeriod, name});
		counts.merge(type, 1, Integer::sum);
	}

	/** 원본을 받은 날 (폴더 수정일, 서울 기준 yyyy-MM-dd) */
	private static String collectedDate(Path dir) {
		try {
			return Files.getLastModifiedTime(dir).toInstant().atZone(SEOUL).toLocalDate().toString();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
