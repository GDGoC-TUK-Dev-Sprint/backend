package com.tukorea.gdgoc.koready.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.domain.place.entity.HoursType;
import com.tukorea.gdgoc.koready.domain.place.service.OpeningHours.Now;
import com.tukorea.gdgoc.koready.domain.place.service.OpeningHours.Status;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
		OpeningHours festival = new OpeningHours(HoursType.ALWAYS_OPEN, List.of(), LocalDate.of(2026, 11, 11), null);

		assertThat(mondayOnly.opensOn(TUESDAY)).isFalse();
		assertThat(mondayOnly.earliestStart(TUESDAY, minutes("10:00"), 30)).isNull();
		assertThat(festival.opensOn(TUESDAY)).isFalse();
		assertThat(festival.opensOn(TUESDAY.plusDays(1))).isTrue();
	}

	@Test
	void seasonalRowWinsOverYearRoundRow() {
		OpeningHours hours = new OpeningHours(HoursType.STRUCTURED, List.of(
				new OpeningHours.Row(2, null, null, time("09:00"), time("18:00"), null, null, null),
				new OpeningHours.Row(2, "11-01", "02-29", time("10:00"), time("17:00"), null, null, null)), null, null);

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

	@Test
	void nowIsOpenWithClosingAndLastEntryTimes() {
		OpeningHours hours = hours(row(2, "09:00", "18:00", "17:00"));

		assertThat(hours.now(at(TUESDAY, "10:00"))).isEqualTo(new Now(Status.OPEN, time("18:00"), time("17:00")));
	}

	@Test
	void nowIsBreakInsideBreakTime() {
		OpeningHours hours = hours(new OpeningHours.Row(2, null, null, time("09:00"), time("18:00"), null,
				time("12:00"), time("13:00")));

		assertThat(hours.now(at(TUESDAY, "12:30"))).isEqualTo(new Now(Status.BREAK, time("18:00"), null));
		assertThat(hours.now(at(TUESDAY, "13:00")).status()).isEqualTo(Status.OPEN);
	}

	@Test
	void nowIsClosedBeforeOpeningAndFromClosingWithoutTimes() {
		OpeningHours hours = hours(row(2, "09:00", "18:00", "17:00"));

		assertThat(hours.now(at(TUESDAY, "08:59"))).isEqualTo(new Now(Status.CLOSED, null, null));
		assertThat(hours.now(at(TUESDAY, "18:00"))).isEqualTo(new Now(Status.CLOSED, null, null));
	}

	@Test
	void nowCountsYesterdaysRowOpenPastMidnight() {
		OpeningHours mondayNight = hours(row(1, "20:00", "02:00", null));

		assertThat(mondayNight.now(at(TUESDAY, "01:00"))).isEqualTo(new Now(Status.OPEN, time("02:00"), null));
		assertThat(mondayNight.now(at(TUESDAY, "02:00")).status()).isEqualTo(Status.CLOSED);
		assertThat(mondayNight.now(at(TUESDAY.minusDays(1), "23:00")).status()).isEqualTo(Status.OPEN);
	}

	@Test
	void nowIsClosedOnRegularClosedDay() {
		OpeningHours mondayOnly = hours(row(1, "09:00", "18:00", null));

		assertThat(mondayOnly.now(at(TUESDAY, "10:00")).status()).isEqualTo(Status.CLOSED);
	}

	@Test
	void nowUsesSeasonalRowOverYearRoundRow() {
		OpeningHours hours = new OpeningHours(HoursType.STRUCTURED, List.of(
				new OpeningHours.Row(2, null, null, time("09:00"), time("18:00"), null, null, null),
				new OpeningHours.Row(2, "11-01", "02-29", time("10:00"), time("17:00"), null, null, null)), null, null);

		assertThat(hours.now(at(TUESDAY, "09:30")).status()).isEqualTo(Status.CLOSED);
		assertThat(hours.now(at(TUESDAY, "16:30"))).isEqualTo(new Now(Status.OPEN, time("17:00"), null));
	}

	@Test
	void nowIsClosedAfterLastEntry() {
		OpeningHours hours = hours(row(2, "09:00", "18:00", "17:00"));
		OpeningHours mondayNight = hours(row(1, "18:00", "02:00", "01:00"));

		assertThat(hours.now(at(TUESDAY, "17:00")).status()).isEqualTo(Status.OPEN);
		assertThat(hours.now(at(TUESDAY, "17:01"))).isEqualTo(new Now(Status.CLOSED, null, null));
		assertThat(mondayNight.now(at(TUESDAY, "01:00")).status()).isEqualTo(Status.OPEN);
		assertThat(mondayNight.now(at(TUESDAY, "01:30")).status()).isEqualTo(Status.CLOSED);
	}

	@Test
	void closedDaysAreWeekdaysWithoutRowsOrOutOfSeason() {
		OpeningHours hours = new OpeningHours(HoursType.STRUCTURED, List.of(
				new OpeningHours.Row(1, "06-01", "08-31", time("09:00"), time("18:00"), null, null, null),
				new OpeningHours.Row(2, null, null, time("09:00"), time("18:00"), null, null, null),
				new OpeningHours.Row(3, "11-01", "02-29", time("10:00"), time("17:00"), null, null, null)), null, null);
		OpeningHours unknown = new OpeningHours(HoursType.UNKNOWN, List.of(), null, null);

		assertThat(hours.closedDays(TUESDAY)).containsExactly(1, 4, 5, 6, 7);
		assertThat(hours.closedDays(LocalDate.of(2026, 7, 7))).containsExactly(3, 4, 5, 6, 7);
		assertThat(unknown.closedDays(TUESDAY)).isEmpty();
	}

	@Test
	void nowOfAlwaysOpenAndUnknownPlaces() {
		OpeningHours unknown = new OpeningHours(HoursType.UNKNOWN, List.of(), null, null);

		assertThat(OpeningHours.UNRESTRICTED.now(at(TUESDAY, "03:00"))).isEqualTo(new Now(Status.OPEN, null, null));
		assertThat(unknown.now(at(TUESDAY, "10:00"))).isEqualTo(new Now(Status.UNKNOWN, null, null));
	}

	@Test
	void nowIsClosedOutsideEventPeriod() {
		OpeningHours festival = new OpeningHours(HoursType.ALWAYS_OPEN, List.of(), LocalDate.of(2026, 11, 11),
				LocalDate.of(2026, 11, 12));

		assertThat(festival.now(at(TUESDAY, "10:00")).status()).isEqualTo(Status.CLOSED);
		assertThat(festival.now(at(TUESDAY.plusDays(1), "10:00")).status()).isEqualTo(Status.OPEN);
		assertThat(festival.now(at(TUESDAY.plusDays(3), "10:00")).status()).isEqualTo(Status.CLOSED);
	}

	private static OpeningHours hours(OpeningHours.Row... rows) {
		return new OpeningHours(HoursType.STRUCTURED, List.of(rows), null, null);
	}

	private static OpeningHours.Row row(int dayOfWeek, String open, String close, String lastEntry) {
		return new OpeningHours.Row(dayOfWeek, null, null, time(open), time(close),
				lastEntry == null ? null : time(lastEntry), null, null);
	}

	private static LocalDateTime at(LocalDate date, String time) {
		return date.atTime(time(time));
	}

	private static LocalTime time(String value) {
		return LocalTime.parse(value);
	}

	private static int minutes(String value) {
		return OpeningHours.minutes(time(value));
	}
}
