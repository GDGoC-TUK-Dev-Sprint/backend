package com.tukorea.gdgoc.koready.domain.trip.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 영업시간 판정. 2026-11-10은 화요일(2)이다. 시각은 0시부터의 분이다.
 */
class OpeningHoursTest {

	private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 10);

	@Test
	void arrivingInsideHoursStartsRightAway() {
		OpeningHours hours = hours(row(2, "09:00", "18:00", null));

		assertThat(hours.earliestStart(TUESDAY, minutes("11:00"), 60)).isEqualTo(minutes("11:00"));
	}

	@Test
	void arrivingBeforeOpeningWaitsUntilOpening() {
		OpeningHours hours = hours(row(2, "10:30", "18:00", null));

		assertThat(hours.earliestStart(TUESDAY, minutes("09:10"), 60)).isEqualTo(minutes("10:30"));
	}

	@Test
	void waitsForTheNextSessionWhenTheFirstIsOver() {
		OpeningHours hours = hours(row(2, "09:00", "11:00", null), row(2, "14:00", "18:00", null));

		assertThat(hours.earliestStart(TUESDAY, minutes("10:30"), 60)).isEqualTo(minutes("14:00"));
	}

	@Test
	void cannotStayAfterLastEntryOrPastClosing() {
		OpeningHours hours = hours(row(2, "09:00", "18:00", "17:00"));

		assertThat(hours.earliestStart(TUESDAY, minutes("17:10"), 30)).isNull();
		assertThat(hours.earliestStart(TUESDAY, minutes("16:30"), 120)).isNull();
	}

	@Test
	void closedWeekdayAndEventPeriodDoNotOpen() {
		OpeningHours mondayOnly = hours(row(1, "09:00", "18:00", null));
		OpeningHours festival = new OpeningHours(false, List.of(), LocalDate.of(2026, 11, 11), null);

		assertThat(mondayOnly.opensOn(TUESDAY)).isFalse();
		assertThat(mondayOnly.earliestStart(TUESDAY, minutes("10:00"), 30)).isNull();
		assertThat(festival.opensOn(TUESDAY)).isFalse();
		assertThat(festival.opensOn(TUESDAY.plusDays(1))).isTrue();
	}

	@Test
	void seasonalRowWinsOverYearRoundRow() {
		OpeningHours hours = new OpeningHours(true, List.of(
				new OpeningHours.Row(2, null, null, time("09:00"), time("18:00"), null),
				new OpeningHours.Row(2, "11-01", "02-29", time("10:00"), time("17:00"), null)), null, null);

		assertThat(hours.earliestStart(TUESDAY, minutes("09:00"), 60)).isEqualTo(minutes("10:00"));
		assertThat(hours.canStay(TUESDAY, time("09:00"), time("10:00"))).isFalse();
	}

	@Test
	void unrestrictedPlaceHasNoLimit() {
		assertThat(OpeningHours.UNRESTRICTED.earliestStart(TUESDAY, minutes("08:00"), 600)).isEqualTo(minutes("08:00"));
		assertThat(OpeningHours.UNRESTRICTED.canStay(TUESDAY, time("23:00"), time("01:00"))).isTrue();
	}

	@Test
	void openMinutesAreCountedInsideTheWindow() {
		OpeningHours hours = hours(row(2, "08:00", "10:30", null), row(2, "20:00", "02:00", null));

		assertThat(hours.openMinutesBetween(TUESDAY, minutes("09:00"), minutes("21:00"))).isEqualTo(90 + 60);
		assertThat(OpeningHours.UNRESTRICTED.openMinutesBetween(TUESDAY, 540, 1260)).isEqualTo(720);
	}

	private static OpeningHours hours(OpeningHours.Row... rows) {
		return new OpeningHours(true, List.of(rows), null, null);
	}

	private static OpeningHours.Row row(int dayOfWeek, String open, String close, String lastEntry) {
		return new OpeningHours.Row(dayOfWeek, null, null, time(open), time(close),
				lastEntry == null ? null : time(lastEntry));
	}

	private static LocalTime time(String value) {
		return LocalTime.parse(value);
	}

	private static int minutes(String value) {
		return OpeningHours.minutes(time(value));
	}
}
