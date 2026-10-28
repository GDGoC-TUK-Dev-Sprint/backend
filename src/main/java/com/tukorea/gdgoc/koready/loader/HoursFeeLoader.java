package com.tukorea.gdgoc.koready.loader;

import com.tukorea.gdgoc.koready.place.FeeType;
import com.tukorea.gdgoc.koready.place.HoursType;
import java.sql.Types;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code place_opening_hours}·{@code place_fees}와 {@code places.hours_type}·{@code fee_type}.
 * {@code places}에 넣은 운영시간·쉬는 날·요금 원문을 규칙으로 나눈다. 다시 돌리면 지우고 다시 넣는다.
 */
@Slf4j
@Component
@Profile("loader")
@RequiredArgsConstructor
class HoursFeeLoader {

	private static final String INSERT_HOURS = """
			INSERT INTO place_opening_hours (place_id, day_of_week, season_from, season_to,
				open_time, close_time, last_entry_time, break_start, break_end)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";
	private static final int[] HOURS_TYPES = {
			Types.BIGINT, Types.SMALLINT, Types.CHAR, Types.CHAR, Types.TIME, Types.TIME, Types.TIME, Types.TIME, Types.TIME};
	private static final String INSERT_FEES =
			"INSERT INTO place_fees (place_id, audience, label_ko, amount_krw) VALUES (?, ?, ?, ?)";
	private static final int[] FEE_TYPES = {Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.INTEGER};

	private record Source(long id, int contentType, String hours, String rest, String fee) {
	}

	private final JdbcTemplate jdbc;

	@Transactional
	public void load() {
		List<Source> places = jdbc.query(
				"SELECT id, content_type_id, hours_text_ko, rest_text_ko, fee_text_ko FROM places ORDER BY id",
				(rs, i) -> new Source(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5)));

		List<Object[]> hourRows = new ArrayList<>();
		List<Object[]> feeRows = new ArrayList<>();
		List<Object[]> types = new ArrayList<>();
		Map<HoursType, Integer> hoursTypes = new EnumMap<>(HoursType.class);
		Map<FeeType, Integer> feeTypes = new EnumMap<>(FeeType.class);
		Map<String, Integer> hoursReasons = new TreeMap<>();
		Map<String, Integer> feeReasons = new TreeMap<>();
		for (Source p : places) {
			OpeningHoursParser.Result hours = OpeningHoursParser.parse(p.hours(), p.rest(), p.contentType());
			FeeParser.Result fee = FeeParser.parse(p.fee());
			for (OpeningHoursParser.Row r : hours.rows()) {
				hourRows.add(new Object[] {p.id(), (short) r.dayOfWeek(), r.seasonFrom(), r.seasonTo(),
						r.open(), r.close(), r.lastEntry(), r.breakStart(), r.breakEnd()});
			}
			for (FeeParser.Fee f : fee.fees()) {
				feeRows.add(new Object[] {p.id(), f.audience().name(), f.labelKo(), f.amountKrw()});
			}
			types.add(new Object[] {hours.type().name(), fee.type().name(), p.id()});
			hoursTypes.merge(hours.type(), 1, Integer::sum);
			feeTypes.merge(fee.type(), 1, Integer::sum);
			if (hours.reason() != null) {
				hoursReasons.merge(hours.reason(), 1, Integer::sum);
			}
			if (fee.reason() != null) {
				feeReasons.merge(fee.type() + " " + fee.reason(), 1, Integer::sum);
			}
		}

		jdbc.update("DELETE FROM place_opening_hours");
		jdbc.update("DELETE FROM place_fees");
		jdbc.batchUpdate(INSERT_HOURS, hourRows, HOURS_TYPES);
		jdbc.batchUpdate(INSERT_FEES, feeRows, FEE_TYPES);
		jdbc.batchUpdate("UPDATE places SET hours_type = ?, fee_type = ? WHERE id = ?", types,
				new int[] {Types.VARCHAR, Types.VARCHAR, Types.BIGINT});
		log.info("place_opening_hours {}행, 운영시간 {} (UNKNOWN 이유 {})", hourRows.size(), hoursTypes, hoursReasons);
		log.info("place_fees {}행, 요금 {} (이유 {})", feeRows.size(), feeTypes, feeReasons);
	}
}
