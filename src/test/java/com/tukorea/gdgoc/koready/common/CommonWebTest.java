package com.tukorea.gdgoc.koready.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@IntegrationTest
@Import(CommonWebTest.ProbeController.class)
class CommonWebTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void apiExceptionBodyHasCodeMessageFieldsAndExtra() {
		assertThat(mvc.get().uri("/test/common/fail"))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "ACTIVE_TRIP_EXISTS", "message": "끝나지 않은 확정 여행이 있습니다", "fields": [], "activeTripId": 42}
						""");
		assertThat(mvc.get().uri("/test/common/fail")).bodyJson().doesNotHavePath("$.extra");
	}

	@Test
	void validationFailureListsFieldsInKorean() {
		assertThat(mvc.post().uri("/test/common/body").contentType(MediaType.APPLICATION_JSON)
				.header("Accept-Language", "en")
				.content("""
						{"name": " ", "budget": {"amount": -1, "currency": "KRW"}}
						"""))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST",
						 "fields": [{"field": "budget.amount", "reason": "0 이상이어야 합니다"}, {"field": "name", "reason": "공백일 수 없습니다"}]}
						""");
	}

	@Test
	void unreadableBodyPointsToField() {
		assertThat(mvc.post().uri("/test/common/body").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "a", "budget": {"amount": 1, "currency": "GBP"}}
						"""))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "budget.currency", "reason": "형식이 맞지 않습니다"}]}
						""");
		assertThat(mvc.post().uri("/test/common/body").contentType(MediaType.APPLICATION_JSON).content("{"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": []}
						""");
	}

	@Test
	void pageQueryHasDefaultsAndLimits() {
		assertThat(mvc.get().uri("/test/common/page"))
				.hasStatusOk()
				.bodyJson()
				.isEqualTo("""
						{"items": [1], "page": 0, "size": 20, "totalCount": 120, "hasNext": true}
						""");
		assertThat(mvc.get().uri("/test/common/page?page=5&size=20")).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
		assertThat(mvc.get().uri("/test/common/page?size=51"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "size"}]}
						""");
		assertThat(mvc.get().uri("/test/common/page?page=abc"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "page", "reason": "형식이 맞지 않습니다"}]}
						""");
	}

	@Test
	void loginUserIdComesFromDevHeader() {
		assertThat(mvc.get().uri("/test/common/me").header("X-Dev-User-Id", "7").header("Accept-Language", "en-US,en;q=0.9"))
				.hasStatusOk()
				.bodyJson()
				.isEqualTo("""
						{"userId": 7, "language": "EN"}
						""");
		assertThat(mvc.get().uri("/test/common/me"))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("UNAUTHORIZED");
		assertThat(mvc.get().uri("/test/common/me").header("X-Dev-User-Id", "abc"))
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void frameworkErrorsKeepStatus() {
		assertThat(mvc.get().uri("/test/common/none"))
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("NOT_FOUND");
		assertThat(mvc.delete().uri("/test/common/fail"))
				.hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
	}

	@Test
	void recordTimeHasSeoulOffset() {
		assertThat(mvc.get().uri("/test/common/time"))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.at").isEqualTo("2026-10-31T14:00:00+09:00");
	}

	@RestController
	@RequestMapping("/test/common")
	static class ProbeController {

		record Body(@NotBlank String name, @Valid Money budget) {
		}

		@GetMapping("/fail")
		void fail() {
			throw new ApiException(ErrorCode.ACTIVE_TRIP_EXISTS, Map.of("activeTripId", 42L));
		}

		@PostMapping("/body")
		Body body(@Valid @RequestBody Body body) {
			return body;
		}

		@GetMapping("/page")
		PageResponse<Integer> page(@Valid PageQuery query) {
			return PageResponse.of(List.of(1), query, 120);
		}

		@GetMapping("/me")
		Map<String, Object> me(@LoginUserId Long userId, Language language) {
			return Map.of("userId", userId, "language", language);
		}

		@GetMapping("/time")
		Map<String, OffsetDateTime> time() {
			return Map.of("at", SeoulTime.of(Instant.parse("2026-10-31T05:00:00Z")));
		}
	}
}
