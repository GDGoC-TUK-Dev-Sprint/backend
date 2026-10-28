package com.tukorea.gdgoc.koready.loader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 장소·데이터랩 통계 적재. {@code loader} 프로필로 켤 때만 돌고, 끝나면 앱이 종료된다.
 * 자연 키로 upsert하므로 다시 돌려도 결과가 같다.
 */
@Slf4j
@Component
@Profile("loader")
@RequiredArgsConstructor
class LoaderRunner implements ApplicationRunner {

	private final PlaceClassLoader placeClassLoader;
	private final PlaceLoader placeLoader;
	private final DatalabLoader datalabLoader;

	@Override
	public void run(ApplicationArguments args) {
		placeClassLoader.load();
		placeLoader.load();
		datalabLoader.load();
		log.info("적재 끝");
	}
}
