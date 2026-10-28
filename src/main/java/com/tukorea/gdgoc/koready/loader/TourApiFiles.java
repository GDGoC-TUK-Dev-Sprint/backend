package com.tukorea.gdgoc.koready.loader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 받아 둔 TourAPI 국문 응답 JSON ({@code tourapi/kor/...})을 읽는다.
 */
@Component
@Profile("loader")
class TourApiFiles {

	private final Path korDir;
	private final JsonMapper jsonMapper;

	TourApiFiles(@Value("${koready.loader.raw-dir}") Path rawDir, JsonMapper jsonMapper) {
		this.korDir = rawDir.resolve("tourapi").resolve("kor");
		this.jsonMapper = jsonMapper;
	}

	/** 목록 응답 ({@code list/page-*.json}) 전체의 item */
	List<JsonNode> listItems() {
		try (Stream<Path> pages = Files.list(korDir.resolve("list"))) {
			List<JsonNode> items = new ArrayList<>();
			pages.filter(p -> p.getFileName().toString().endsWith(".json")).sorted()
					.forEach(p -> items.addAll(items(p)));
			return items;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** 신분류 코드표 ({@code code/lclsSystm.json})의 item */
	List<JsonNode> classCodeItems() {
		return items(korDir.resolve("code").resolve("lclsSystm.json"));
	}

	/** 장소별 상세 응답 ({@code common}·{@code intro}·{@code info})의 item. 파일이 없으면 빈 목록 */
	List<JsonNode> detailItems(String kind, String contentId) {
		Path file = korDir.resolve(kind).resolve(contentId + ".json");
		return Files.exists(file) ? items(file) : List.of();
	}

	String toJson(Object node) {
		return jsonMapper.writeValueAsString(node);
	}

	/** {@code items}가 빈 문자열이거나 {@code item}이 배열이 아닌 응답도 목록으로 돌려준다 */
	private List<JsonNode> items(Path file) {
		JsonNode item = jsonMapper.readTree(file.toFile()).path("response").path("body").path("items").path("item");
		if (item.isArray()) {
			return List.copyOf(item.values());
		}
		return item.isObject() ? List.of(item) : List.of();
	}

	/** 빈 문자열은 null */
	static String text(JsonNode node, String field) {
		String value = node.path(field).asString("");
		return value.isBlank() ? null : value;
	}
}
