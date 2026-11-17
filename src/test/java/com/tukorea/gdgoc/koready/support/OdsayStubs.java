package com.tukorea.gdgoc.koready.support;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.infra.odsay.OdsayClient;
import com.tukorea.gdgoc.koready.infra.odsay.OdsayRoute;
import java.util.List;
import java.util.Optional;

/** 가짜 {@link OdsayClient}의 여러 구간 받기를 구간마다 {@code searchPath}로 돌린다 (테스트는 {@code searchPath}만 정하고 센다) */
public final class OdsayStubs {

	private OdsayStubs() {
	}

	public static void searchPathsCallSearchPath(OdsayClient odsay) {
		when(odsay.searchPaths(anyList())).thenAnswer(invocation -> {
			List<OdsayClient.Endpoints> requests = invocation.getArgument(0);
			return requests.stream().map(request -> {
				try {
					return Optional.ofNullable(odsay.searchPath(request.from(), request.to()));
				} catch (ApiException e) {
					return Optional.<OdsayRoute>empty();
				}
			}).toList();
		});
	}
}
