package com.tukorea.gdgoc.koready.domain.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tukorea.gdgoc.koready.domain.trip.config.RouteProperties;
import com.tukorea.gdgoc.koready.domain.trip.entity.LegMode;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import com.tukorea.gdgoc.koready.infra.odsay.OdsayClient;
import com.tukorea.gdgoc.koready.infra.odsay.OdsayRoute;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * 저장하는 이동 구간 계산. 숙소는 명동이고 위도 0.002 ≈ 220m(도보), 0.03 ≈ 3.3km(대중교통)다.
 */
class OdsayLegRouterTest {

	private static final LatLng STAY = new LatLng(37.5636, 126.9826);
	private static final LatLng NEAR = new LatLng(37.5656, 126.9826);
	private static final LatLng FAR = new LatLng(37.5936, 126.9826);

	private final RouteProperties properties = new RouteProperties(LocalTime.of(9, 0), LocalTime.of(21, 0), 1000, 1.3,
			67, 333, 10);
	private final EstimatedLegRouter estimated = new EstimatedLegRouter(properties);
	private final OdsayClient odsay = mock(OdsayClient.class);
	private final OdsayLegRouter router = new OdsayLegRouter(estimated, odsay);

	/** 도보 거리는 ODsay를 부르지 않는다 (ODsay는 700m 안을 거절하고, 도보는 운임이 0이다) */
	@Test
	void walkingDistanceDoesNotCallOdsay() {
		LegRoute route = router.route(STAY, NEAR);

		assertThat(route.mode()).isEqualTo(LegMode.WALK);
		assertThat(route.fareKrw()).isZero();
		assertThat(route.estimated()).isTrue();
		verifyNoInteractions(odsay);
	}

	@Test
	void transitDistanceUsesOdsay() {
		when(odsay.enabled()).thenReturn(true);
		when(odsay.searchPath(STAY, FAR)).thenReturn(new OdsayRoute(19, 4629, 1550));

		assertThat(router.route(STAY, FAR)).isEqualTo(new LegRoute(LegMode.TRANSIT, 4629, 19, 1550, false));
	}

	/** ODsay가 실패하면 좌표 추정값(운임 없음)이다 */
	@Test
	void odsayFailureFallsBackToEstimate() {
		when(odsay.enabled()).thenReturn(true);
		when(odsay.searchPath(any(), any())).thenThrow(new ApiException(ErrorCode.EXTERNAL_API_FAILED, "실패"));

		LegRoute route = router.route(STAY, FAR);

		assertThat(route).isEqualTo(estimated.route(STAY, FAR));
		assertThat(route.mode()).isEqualTo(LegMode.TRANSIT);
		assertThat(route.fareKrw()).isNull();
		assertThat(route.estimated()).isTrue();
	}

	@Test
	void withoutKeyUsesEstimate() {
		assertThat(router.route(STAY, FAR)).isEqualTo(estimated.route(STAY, FAR));
		verify(odsay, never()).searchPath(any(), any());
	}
}
