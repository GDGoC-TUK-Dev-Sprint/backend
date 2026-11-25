package com.tukorea.gdgoc.koready.domain.placedetail.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tukorea.gdgoc.koready.domain.place.dto.PlaceSummary;
import com.tukorea.gdgoc.koready.domain.place.entity.FeeAudience;
import com.tukorea.gdgoc.koready.domain.place.entity.FeeType;
import com.tukorea.gdgoc.koready.domain.place.entity.HoursType;
import com.tukorea.gdgoc.koready.domain.place.service.OpeningHours;
import com.tukorea.gdgoc.koready.global.dto.Code;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.dto.Money;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 장소 상세. 장소 요약과 같은 필드에 소개·사진·운영시간·요금을 더하고, 로그인하면 일정 포함·찜 여부를 채운다.
 *
 * @param summary 한 줄 소개. 없으면 null (만들어 넣지 않는다)
 * @param images 사진. 지금은 대표 1장이고 없으면 빈 목록
 * @param inTrip 로그인했고 그 여행(내 여행)에 담긴 장소일 때만. 그 밖에는 null
 * @param favorited 찜 여부. 로그인하지 않으면 false
 */
public record PlaceDetailResponse(
		Long placeId,
		String name,
		String nameKo,
		Code category,
		Code classification,
		LatLng location,
		String summary,
		List<PlaceSummary.Image> images,
		String address,
		Hours hours,
		Fees fees,
		String overview,
		InTrip inTrip,
		boolean favorited) {

	/**
	 * 운영시간.
	 *
	 * @param weekly 저장된 요일별 행 그대로 (요일·계절 순)
	 * @param closedDays 정기 휴무 요일 (행이 없는 요일). {@code STRUCTURED}가 아니면 빈 목록
	 * @param text 운영시간 원문
	 * @param restText 쉬는 날 원문
	 */
	public record Hours(HoursType type, Now now, List<Weekly> weekly, List<String> closedDays, String text,
			String restText) {
	}

	/** 지금(서울) 운영 상태. 닫는 시각·입장 마감은 {@code OPEN}·{@code BREAK}일 때만 준다 */
	public record Now(
			OpeningHours.Status status,
			@JsonFormat(pattern = "HH:mm") LocalTime closesAt,
			@JsonFormat(pattern = "HH:mm") LocalTime lastEntryAt) {
	}

	/**
	 * 요일 하나의 운영시간.
	 *
	 * @param dayOfWeek {@code MON} … {@code SUN}
	 * @param seasonFrom MM-DD. 연중이면 null. {@code seasonTo}보다 뒤면 해를 넘긴다
	 */
	public record Weekly(
			String dayOfWeek,
			@JsonFormat(pattern = "HH:mm") LocalTime openTime,
			@JsonFormat(pattern = "HH:mm") LocalTime closeTime,
			@JsonFormat(pattern = "HH:mm") LocalTime lastEntryTime,
			@JsonFormat(pattern = "HH:mm") LocalTime breakStart,
			@JsonFormat(pattern = "HH:mm") LocalTime breakEnd,
			String seasonFrom,
			String seasonTo) {
	}

	/** 요금. {@code text}는 요금 원문 */
	public record Fees(FeeType type, List<FeeItem> items, String text) {
	}

	/** 대상별 요금. 0이면 무료 */
	public record FeeItem(FeeAudience audience, String label, Money amount) {
	}

	/** 그 여행에서 이 장소가 처음 나오는 일정 항목 */
	public record InTrip(Long tripId, int dayNumber, LocalDate date, @JsonFormat(pattern = "HH:mm") LocalTime arriveTime) {
	}
}
