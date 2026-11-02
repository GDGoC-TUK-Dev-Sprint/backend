package com.tukorea.gdgoc.koready.infra.naver;

import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * 네이버 클라이언트들(Maps·지역 검색)이 같이 쓰는 시간 제한과 실패 변환.
 */
final class NaverHttp {

	private NaverHttp() {
	}

	static JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(readTimeout);
		return factory;
	}

	/** 네이버를 부르고 응답을 읽는다. 그 사이의 실패는 모두 502다 (원인은 서버 로그에만 남는다) */
	static <T> T call(String api, Supplier<T> request) {
		try {
			return request.get();
		} catch (ApiException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new ApiException(ErrorCode.EXTERNAL_API_FAILED, "네이버 " + api + " 호출에 실패했습니다", e);
		}
	}
}
