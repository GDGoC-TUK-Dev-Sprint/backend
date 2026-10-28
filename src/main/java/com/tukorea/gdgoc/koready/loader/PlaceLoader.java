package com.tukorea.gdgoc.koready.loader;

import static com.tukorea.gdgoc.koready.loader.TourApiFiles.text;

import com.tukorea.gdgoc.koready.place.FeeType;
import com.tukorea.gdgoc.koready.place.HoursType;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * {@code places}: TourAPI 국문 서울 목록 + 공통(개요)·소개·반복정보.
 * 운영시간·요금은 원문만 넣고 유형은 {@code UNKNOWN}으로 둔다 (나누는 것은 {@link HoursFeeLoader}).
 * 원문의 {@code <br>} 태그는 줄바꿈으로 바꾼다.
 */
@Slf4j
@Component
@Profile("loader")
@RequiredArgsConstructor
class PlaceLoader {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter TOURAPI_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	/** 여행코스는 장소로 넣지 않는다 */
	private static final int COURSE_TYPE = 25;

	/** 서울을 넉넉히 감싼 범위 (경도·위도). 밖에 있는 좌표는 잘못된 값이라 적재하지 않는다 */
	private static final double MIN_X = 126.7, MAX_X = 127.3, MIN_Y = 37.4, MAX_Y = 37.75;

	/** 콘텐츠 유형별 소개정보 원문 필드. 숙박(32)은 없다. 관광지(12) 요금은 반복정보 "입장료" */
	private record TextFields(String hours, String rest, String fee, String stay) {
	}

	private static final Map<Integer, TextFields> TEXT_FIELDS = Map.of(
			12, new TextFields("usetime", "restdate", null, null),
			14, new TextFields("usetimeculture", "restdateculture", "usefee", "spendtime"),
			15, new TextFields("playtime", null, "usetimefestival", "spendtimefestival"),
			28, new TextFields("usetimeleports", "restdateleports", "usefeeleports", null),
			38, new TextFields("opentime", "restdateshopping", null, null),
			39, new TextFields("opentimefood", "restdatefood", null, null));

	private static final String UPSERT = """
			INSERT INTO places (
				content_id, content_type_id, class1_code, class2_code, class3_code, name_ko,
				ldong_regn_cd, ldong_signgu_cd, addr1, addr2, location,
				overview_ko, image_url, thumbnail_url, image_license,
				hours_type, hours_text_ko, rest_text_ko, fee_type, fee_text_ko,
				stay_minutes, stay_basis, event_start_date, event_end_date,
				signal_count, intro_raw, info_raw, source_modified_at, synced_at, active)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
				?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?::jsonb, ?::jsonb, ?, ?, true)
			ON CONFLICT (content_id) DO UPDATE SET
				content_type_id = EXCLUDED.content_type_id,
				class1_code = EXCLUDED.class1_code,
				class2_code = EXCLUDED.class2_code,
				class3_code = EXCLUDED.class3_code,
				name_ko = EXCLUDED.name_ko,
				ldong_regn_cd = EXCLUDED.ldong_regn_cd,
				ldong_signgu_cd = EXCLUDED.ldong_signgu_cd,
				addr1 = EXCLUDED.addr1,
				addr2 = EXCLUDED.addr2,
				location = EXCLUDED.location,
				overview_ko = EXCLUDED.overview_ko,
				image_url = EXCLUDED.image_url,
				thumbnail_url = EXCLUDED.thumbnail_url,
				image_license = EXCLUDED.image_license,
				hours_type = EXCLUDED.hours_type,
				hours_text_ko = EXCLUDED.hours_text_ko,
				rest_text_ko = EXCLUDED.rest_text_ko,
				fee_type = EXCLUDED.fee_type,
				fee_text_ko = EXCLUDED.fee_text_ko,
				stay_minutes = EXCLUDED.stay_minutes,
				stay_basis = EXCLUDED.stay_basis,
				event_start_date = EXCLUDED.event_start_date,
				event_end_date = EXCLUDED.event_end_date,
				intro_raw = EXCLUDED.intro_raw,
				info_raw = EXCLUDED.info_raw,
				source_modified_at = EXCLUDED.source_modified_at,
				synced_at = EXCLUDED.synced_at,
				active = true
			""";
	private static final int[] TYPES = {
			Types.VARCHAR, Types.SMALLINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
			Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.DOUBLE, Types.DOUBLE,
			Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
			Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
			Types.SMALLINT, Types.VARCHAR, Types.DATE, Types.DATE,
			Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE};

	private final TourApiFiles files;
	private final JdbcTemplate jdbc;

	@Transactional
	public void load() {
		OffsetDateTime syncedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
		List<JsonNode> items = files.listItems();
		Map<String, String> signguByGu = signguCodeByGu(items);

		List<Object[]> rows = new ArrayList<>();
		int course = 0, badLocation = 0, noSigngu = 0, filledSigngu = 0;
		for (JsonNode item : items) {
			int contentType = Integer.parseInt(text(item, "contenttypeid"));
			if (contentType == COURSE_TYPE) {
				course++;
				continue;
			}
			double x = Double.parseDouble(text(item, "mapx"));
			double y = Double.parseDouble(text(item, "mapy"));
			if (x < MIN_X || x > MAX_X || y < MIN_Y || y > MAX_Y) {
				log.warn("좌표가 서울 밖이라 뺌: {} {} ({}, {})", text(item, "contentid"), text(item, "title"), x, y);
				badLocation++;
				continue;
			}
			String signgu = text(item, "lDongSignguCd");
			if (signgu == null) {
				// 법정동 시군구가 빈 장소는 주소의 구 이름으로 다른 장소의 코드를 쓴다
				signgu = signguByGu.get(gu(text(item, "addr1")));
				if (signgu == null) {
					log.warn("법정동 시군구를 정할 수 없어 뺌: {} {}", text(item, "contentid"), text(item, "title"));
					noSigngu++;
					continue;
				}
				filledSigngu++;
			}
			rows.add(row(item, contentType, x, y, signgu, syncedAt));
		}

		jdbc.batchUpdate(UPSERT, rows, TYPES);
		int inactive = jdbc.update("UPDATE places SET active = false WHERE active AND synced_at <> ?", syncedAt);
		log.info("places {}행 (목록 {}건, 코스 {}·좌표 오류 {}·시군구 없음 {} 뺌, 시군구 주소로 채움 {}), 목록에 없어 active=false {}행",
				rows.size(), items.size(), course, badLocation, noSigngu, filledSigngu, inactive);
	}

	private Object[] row(JsonNode item, int contentType, double x, double y, String signgu, OffsetDateTime syncedAt) {
		String contentId = text(item, "contentid");
		String class1 = text(item, "lclsSystm1");
		String class2 = text(item, "lclsSystm2");
		JsonNode common = first(files.detailItems("common", contentId));
		JsonNode intro = first(files.detailItems("intro", contentId));
		List<JsonNode> info = files.detailItems("info", contentId);

		TextFields fields = TEXT_FIELDS.getOrDefault(contentType, new TextFields(null, null, null, null));
		String feeText = contentType == 12 ? admissionFee(info) : field(intro, fields.fee());
		StayMinutes.Result stay = StayMinutes.of(class1, class2, field(intro, fields.stay()));

		return new Object[] {
				contentId, (short) contentType, class1, class2, text(item, "lclsSystm3"), text(item, "title"),
				text(item, "lDongRegnCd"), signgu, text(item, "addr1"), text(item, "addr2"), x, y,
				SourceText.clean(field(common, "overview")), text(item, "firstimage"), text(item, "firstimage2"),
				text(item, "cpyrhtDivCd"),
				HoursType.UNKNOWN.name(), SourceText.clean(field(intro, fields.hours())), SourceText.clean(field(intro, fields.rest())),
				FeeType.UNKNOWN.name(), SourceText.clean(feeText),
				(short) stay.minutes(), stay.basis().name(),
				contentType == 15 ? date(field(intro, "eventstartdate")) : null,
				contentType == 15 ? date(field(intro, "eventenddate")) : null,
				intro == null ? null : files.toJson(intro),
				info.isEmpty() ? null : files.toJson(info),
				LocalDateTime.parse(text(item, "modifiedtime"), TOURAPI_TIME).atZone(SEOUL).toOffsetDateTime(),
				syncedAt};
	}

	/** 반복정보의 "입장료" 항목 (항목명에 공백이 섞여 있다). 여럿이면 줄을 바꿔 잇는다 */
	private static String admissionFee(List<JsonNode> info) {
		String joined = info.stream()
				.filter(r -> "입장료".equals(String.valueOf(text(r, "infoname")).replaceAll("\\s", "")))
				.map(r -> text(r, "infotext"))
				.filter(t -> t != null)
				.collect(Collectors.joining("\n"));
		return joined.isEmpty() ? null : joined;
	}

	/** 주소의 구 이름 → 법정동 시군구 코드 (가장 많이 쓰인 코드) */
	private static Map<String, String> signguCodeByGu(List<JsonNode> items) {
		Map<String, Map<String, Integer>> counts = new HashMap<>();
		for (JsonNode item : items) {
			String gu = gu(text(item, "addr1"));
			String code = text(item, "lDongSignguCd");
			if (gu != null && code != null) {
				counts.computeIfAbsent(gu, k -> new HashMap<>()).merge(code, 1, Integer::sum);
			}
		}
		Map<String, String> result = new HashMap<>();
		counts.forEach((gu, byCode) -> result.put(gu,
				byCode.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey()));
		return result;
	}

	/** "서울특별시 관악구 봉천로 209" → "관악구" */
	private static String gu(String addr1) {
		if (addr1 == null) {
			return null;
		}
		String[] tokens = addr1.strip().split("\\s+");
		return tokens.length > 1 && tokens[1].endsWith("구") ? tokens[1] : null;
	}

	private static JsonNode first(List<JsonNode> items) {
		return items.isEmpty() ? null : items.getFirst();
	}

	private static String field(JsonNode node, String name) {
		return node == null || name == null ? null : text(node, name);
	}

	private static LocalDate date(String yyyymmdd) {
		return yyyymmdd == null ? null : LocalDate.parse(yyyymmdd, DateTimeFormatter.BASIC_ISO_DATE);
	}
}
