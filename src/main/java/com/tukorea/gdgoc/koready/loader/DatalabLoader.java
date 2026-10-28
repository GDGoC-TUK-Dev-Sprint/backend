package com.tukorea.gdgoc.koready.loader;

import com.tukorea.gdgoc.koready.datalab.Industry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데이터랩 국가별 방한현황(빅데이터) CSV ({@code datalab-card/{국가}/}) → {@code datalab_*} 4종.
 * 값은 계산하지 않고 CSV 그대로 넣는다.
 */
@Slf4j
@Component
@Profile("loader")
class DatalabLoader {

	/** 국가 폴더 이름 → ISO 3166-1 코드 */
	private static final Map<String, String> COUNTRY_CODES = Map.of("중국", "CN", "일본", "JP", "미국", "US");

	private static final Map<String, Industry> INDUSTRIES = Map.of(
			"쇼핑업", Industry.SHOPPING, "식음료업", Industry.FOOD, "여가서비스업", Industry.LEISURE,
			"운송업", Industry.TRANSPORT, "숙박업", Industry.LODGING, "의료웰니스업", Industry.MEDICAL,
			"여행업", Industry.TRAVEL);

	private static final String UPSERT_MONTH_TOTALS = """
			INSERT INTO datalab_month_totals (ym, card_spend_krw, visitors) VALUES (?, ?, ?)
			ON CONFLICT (ym) DO UPDATE SET card_spend_krw = EXCLUDED.card_spend_krw, visitors = EXCLUDED.visitors
			""";
	private static final String UPSERT_COUNTRY_MONTH = """
			INSERT INTO datalab_country_month (country_code, ym, card_spend_share_pct, visitor_share_pct)
			VALUES (?, ?, ?, ?)
			ON CONFLICT (country_code, ym) DO UPDATE SET
				card_spend_share_pct = EXCLUDED.card_spend_share_pct,
				visitor_share_pct = EXCLUDED.visitor_share_pct
			""";
	private static final String UPSERT_COUNTRY_INDUSTRY = """
			INSERT INTO datalab_country_industry (country_code, ym, industry, share_pct) VALUES (?, ?, ?, ?)
			ON CONFLICT (country_code, ym, industry) DO UPDATE SET share_pct = EXCLUDED.share_pct
			""";
	private static final String UPSERT_COUNTRY_REGION = """
			INSERT INTO datalab_country_region
				(country_code, period_from, period_to, sido_name, spend_share_pct, visit_share_pct)
			VALUES (?, ?, ?, ?, ?, ?)
			ON CONFLICT (country_code, period_from, sido_name) DO UPDATE SET
				period_to = EXCLUDED.period_to,
				spend_share_pct = EXCLUDED.spend_share_pct,
				visit_share_pct = EXCLUDED.visit_share_pct
			""";

	private final Path cardDir;
	private final JdbcTemplate jdbc;

	DatalabLoader(@Value("${koready.loader.raw-dir}") Path rawDir, JdbcTemplate jdbc) {
		this.cardDir = rawDir.resolve("datalab-card");
		this.jdbc = jdbc;
	}

	@Transactional
	public void load() {
		Map<String, Object[]> monthTotals = null;
		List<Object[]> countryMonths = new ArrayList<>();
		List<Object[]> countryIndustries = new ArrayList<>();
		List<Object[]> countryRegions = new ArrayList<>();

		for (Path dir : countryDirs()) {
			String country = COUNTRY_CODES.get(dir.getFileName().toString());

			// 전체 외국인 값은 어느 국가 파일에나 같은 값이 들어 있다
			Map<String, Object[]> totals = monthTotals(dir);
			if (monthTotals == null) {
				monthTotals = totals;
			} else if (!sameRows(monthTotals, totals)) {
				throw new IllegalStateException("전체 외국인 월별 값이 국가 파일마다 다르다: " + dir);
			}

			Map<String, String> spendShare = byYm(readCsv(find(dir, "국가별 외국인 신용카드")), "조회기간 관광소비비율");
			Map<String, String> visitShare = byYm(readCsv(find(dir, "국가별 관광객 방문비율")), "조회기간 방문비율");
			if (!spendShare.keySet().equals(visitShare.keySet())) {
				throw new IllegalStateException("국가 소비 비율과 방문 비율의 월이 다르다: " + dir);
			}
			spendShare.forEach((ym, share) ->
					countryMonths.add(new Object[] {country, ym, decimal(share), decimal(visitShare.get(ym))}));

			Set<String> yms = new LinkedHashSet<>();
			for (Map<String, String> r : readCsv(find(dir, "업종별 신용카드"))) {
				Industry industry = Objects.requireNonNull(INDUSTRIES.get(r.get("업종별")), "모르는 업종: " + r.get("업종별"));
				countryIndustries.add(new Object[] {country, r.get("기준년월"), industry.name(), decimal(r.get("비율"))});
				yms.add(r.get("기준년월"));
			}

			// 지역 파일은 조회 기간 전체 값이다. 기간은 같이 받은 월별 파일의 처음~끝 월
			String periodFrom = yms.stream().min(String::compareTo).orElseThrow();
			String periodTo = yms.stream().max(String::compareTo).orElseThrow();
			Map<String, String> spendRegion = sidoShares(readCsv(find(dir, "지역별 신용카드 소비비율")), "시도 소비 비율");
			Map<String, String> visitRegion = sidoShares(readCsv(find(dir, "지역별 방문비율")), "시도 방문자 비율");
			// 시도 목록이 두 파일에서 다르다 (소비에만 "기타(온라인)" 등). 한쪽에만 있으면 다른 쪽은 NULL
			Set<String> sidos = new LinkedHashSet<>(spendRegion.keySet());
			sidos.addAll(visitRegion.keySet());
			for (String sido : sidos) {
				countryRegions.add(new Object[] {country, periodFrom, periodTo, sido,
						decimalOrNull(spendRegion.get(sido)), decimalOrNull(visitRegion.get(sido))});
			}
		}

		List<Object[]> totals = new ArrayList<>(Objects.requireNonNull(monthTotals, "국가 폴더가 없다").values());
		jdbc.batchUpdate(UPSERT_MONTH_TOTALS, totals, new int[] {Types.CHAR, Types.BIGINT, Types.BIGINT});
		jdbc.batchUpdate(UPSERT_COUNTRY_MONTH, countryMonths,
				new int[] {Types.CHAR, Types.CHAR, Types.NUMERIC, Types.NUMERIC});
		jdbc.batchUpdate(UPSERT_COUNTRY_INDUSTRY, countryIndustries,
				new int[] {Types.CHAR, Types.CHAR, Types.VARCHAR, Types.NUMERIC});
		jdbc.batchUpdate(UPSERT_COUNTRY_REGION, countryRegions,
				new int[] {Types.CHAR, Types.CHAR, Types.CHAR, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC});
		log.info("datalab_month_totals {}행, datalab_country_month {}행, datalab_country_industry {}행, datalab_country_region {}행",
				totals.size(), countryMonths.size(), countryIndustries.size(), countryRegions.size());
	}

	private List<Path> countryDirs() {
		try (Stream<Path> dirs = Files.list(cardDir)) {
			List<Path> result = dirs.filter(Files::isDirectory).sorted().toList();
			for (Path dir : result) {
				if (!COUNTRY_CODES.containsKey(dir.getFileName().toString())) {
					throw new IllegalStateException("국가 코드를 모르는 폴더: " + dir);
				}
			}
			return result;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** 기준년월 → (ym, 소비액, 방문자 수). 금액은 지수 표기("1.28811382624E12")로 들어 있다 */
	private static Map<String, Object[]> monthTotals(Path dir) {
		Map<String, String> spend = byYm(readCsv(find(dir, "전체 외국인 신용카드")), "조회기간 소비액");
		Map<String, String> visitors = byYm(readCsv(find(dir, "전체 외국인 방문자수")), "조회기간 방문자 수");
		if (!spend.keySet().equals(visitors.keySet())) {
			throw new IllegalStateException("전체 소비액과 방문자 수의 월이 다르다: " + dir);
		}
		Map<String, Object[]> result = new TreeMap<>();
		spend.forEach((ym, amount) -> result.put(ym, new Object[] {ym,
				new BigDecimal(amount).longValueExact(), new BigDecimal(visitors.get(ym)).longValueExact()}));
		return result;
	}

	private static boolean sameRows(Map<String, Object[]> a, Map<String, Object[]> b) {
		return a.keySet().equals(b.keySet())
				&& a.keySet().stream().allMatch(ym -> Arrays.equals(a.get(ym), b.get(ym)));
	}

	private static Map<String, String> byYm(List<Map<String, String>> rows, String column) {
		Map<String, String> result = new LinkedHashMap<>();
		for (Map<String, String> r : rows) {
			result.put(r.get("기준년월"), r.get(column));
		}
		return result;
	}

	/** 시군구 행마다 같은 시도 값이 반복된다 → 시도마다 하나 */
	private static Map<String, String> sidoShares(List<Map<String, String>> rows, String column) {
		Map<String, String> result = new LinkedHashMap<>();
		for (Map<String, String> r : rows) {
			String previous = result.putIfAbsent(r.get("시도명"), r.get(column));
			if (previous != null && !previous.equals(r.get(column))) {
				throw new IllegalStateException("시도 비율이 행마다 다르다: " + r.get("시도명"));
			}
		}
		return result;
	}

	/** 파일 이름에 keyword가 들어간 CSV 하나 */
	private static Path find(Path dir, String keyword) {
		try (Stream<Path> files = Files.list(dir)) {
			List<Path> found = files.filter(p -> p.getFileName().toString().contains(keyword)).toList();
			if (found.size() != 1) {
				throw new IllegalStateException(dir + "에 \"" + keyword + "\" 파일이 " + found.size() + "개다");
			}
			return found.getFirst();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** UTF-8(BOM) CSV. 데이터랩 파일에는 따옴표로 감싼 값이 없다 */
	private static List<Map<String, String>> readCsv(Path file) {
		try {
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			String[] header = lines.getFirst().replace("﻿", "").split(",", -1);
			List<Map<String, String>> rows = new ArrayList<>();
			for (String line : lines.subList(1, lines.size())) {
				if (line.isBlank()) {
					continue;
				}
				if (line.contains("\"")) {
					throw new IllegalStateException("따옴표가 있는 CSV는 읽지 않는다: " + file);
				}
				String[] values = line.split(",", -1);
				Map<String, String> row = new LinkedHashMap<>();
				for (int i = 0; i < header.length; i++) {
					row.put(header[i], i < values.length ? values[i].strip() : "");
				}
				rows.add(row);
			}
			return rows;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static BigDecimal decimal(String value) {
		return new BigDecimal(Objects.requireNonNull(value, "값이 없다"));
	}

	private static BigDecimal decimalOrNull(String value) {
		return value == null ? null : new BigDecimal(value);
	}
}
