package com.pawmento.community.adapter.in.web.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.community.application.port.in.report.ListReportItemResult;
import com.pawmento.community.application.port.in.report.ListReportItemResult.PostState;
import com.pawmento.community.application.port.in.report.ListReportItemResult.ReasonType;
import com.pawmento.community.application.port.in.report.ListReportsQuery;
import com.pawmento.community.application.port.in.report.ListReportsUseCase;
import com.pawmento.community.application.port.in.report.ReportStatus;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.global.security.JwtClaims;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = AdminReportControllerTest.TestController.class,
		includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
				pattern = "com\\.pawmento\\.global\\.security\\.(SecurityConfig|SecurityErrorResponder)"))
@Import(AdminReportControllerTest.TestController.class)
class AdminReportControllerTest {
	private static final String REPORTS = "/api/v1/admin/reports";
	@Autowired MockMvc mvc;
	@Autowired JsonMapper jsonMapper;
	@MockitoBean ListReportsUseCase listReports;
	@MockitoBean JwtDecoder jwtDecoder;

	@Test
	@DisplayName("#92-S1 실제 MVC 응답은 목록 항목 7개와 UTC 날짜 형식을 반환한다")
	void returnsListWithWireFormat() throws Exception {
		var item = new ListReportItemResult(101L, 201L, ReportStatus.PENDING, ReasonType.PRIVACY, Instant.EPOCH,
				"신고 당시 제목", PostState.DELETED);
		when(listReports.list(new ListReportsQuery(ReportStatus.PENDING, 20, null)))
				.thenReturn(new ListReportsUseCase.Page(List.of(item), 101L));

		mvc.perform(get(REPORTS).with(actor("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nextCursor").value("101"))
				.andExpect(jsonPath("$.items[0].createdAt").value("1970-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.items[0].snapshotTitle").value("신고 당시 제목"))
				.andExpect(jsonPath("$.items[0].postState").value("DELETED"))
				.andExpect(response -> {
					var itemJson = jsonMapper.readTree(response.getResponse().getContentAsByteArray()).get("items").get(0);
					assertThat(itemJson.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder(
							"reportId", "postId", "status", "reasonType", "createdAt", "snapshotTitle", "postState");
				});
	}

	@Test
	@DisplayName("#92-S1 동명 헤더만 있으면 목록 조회의 쿼리 기본값을 사용한다")
	void ignoresListConditionsInHeaders() throws Exception {
		var query = new ListReportsQuery(ReportStatus.PENDING, 20, null);
		when(listReports.list(any(ListReportsQuery.class))).thenReturn(new ListReportsUseCase.Page(List.of(), null));

		mvc.perform(get(REPORTS).header("Status", "RESOLVED").header("Size", "5").header("Cursor", "100")
						.with(actor("ADMIN")))
				.andExpect(status().isOk());

		verify(listReports).list(query);
	}

	@Test
	@DisplayName("#92-S1 명시한 목록 조회 조건은 동명 헤더와 무관하게 URL에서 읽는다")
	void bindsExplicitListConditions() throws Exception {
		var query = new ListReportsQuery(ReportStatus.RESOLVED, 5, 100L);
		when(listReports.list(query)).thenReturn(new ListReportsUseCase.Page(List.of(), null));

		mvc.perform(get(REPORTS).queryParam("status", "RESOLVED").queryParam("size", "5").queryParam("cursor", "100")
						.header("Status", "PENDING").header("Size", "20").header("Cursor", "999")
						.with(actor("ADMIN")))
				.andExpect(status().isOk());

		verify(listReports).list(query);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			status | ''      | status는 PENDING, RESOLVED, DISMISSED 중 하나여야 해요.
			status | pending | status는 PENDING, RESOLVED, DISMISSED 중 하나여야 해요.
			size   | ''      | size는 1 이상 50 이하의 정수여야 해요.
			size   | 0       | size는 1 이상 50 이하의 정수여야 해요.
			cursor | ''      | cursor는 양의 정수 신고 ID여야 해요.
			cursor | abc     | cursor는 양의 정수 신고 ID여야 해요.
			""")
	@DisplayName("#92-S5 명시적으로 비었거나 잘못된 목록 입력은 기존 400 코드와 문구를 반환한다")
	void mapsBadInputToValidationError(String field, String input, String message) throws Exception {
		mvc.perform(get(REPORTS).queryParam(field, input).with(actor("ADMIN")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION"))
				.andExpect(jsonPath("$.message").value(message));

		verifyNoInteractions(listReports);
	}

	@Test
	@DisplayName("#92-S5 인코딩 표기가 달라도 같은 목록 쿼리 키를 중복 지정할 수 없다")
	void rejectsDuplicateEncodedQueryKey() throws Exception {
		mvc.perform(get(URI.create(REPORTS + "?size=5&%73ize=6")).with(actor("ADMIN")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION"))
				.andExpect(jsonPath("$.message").value("size는 1 이상 50 이하의 정수여야 해요."));

		verifyNoInteractions(listReports);
	}

	@Test
	@DisplayName("#92-S4 실제 관리자 보안 필터에서 비로그인과 일반 회원은 유스케이스에 접근하지 못한다")
	void rejectsRequestsOutsideAdminRole() throws Exception {
		mvc.perform(get(REPORTS)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mvc.perform(get(REPORTS).with(actor("USER"))).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		verifyNoInteractions(listReports);
	}

	@Test
	@DisplayName("#92-S4 검증할 수 없는 액세스 토큰은 관리자 신고 목록에 접근하지 못한다")
	void rejectsInvalidAccessToken() throws Exception {
		when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));

		mvc.perform(get(REPORTS).cookie(new Cookie(AccessTokenCookie.NAME, "invalid-token")))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		verifyNoInteractions(listReports);
	}

	private RequestPostProcessor actor(String role) {
		String token = "test-token-" + role;
		when(jwtDecoder.decode(token)).thenReturn(Jwt.withTokenValue(token)
				.header("alg", "HS256").subject("8").claim(JwtClaims.ROLE, role).build());
		return request -> {
			request.setCookies(new Cookie(AccessTokenCookie.NAME, token));
			return request;
		};
	}

	@TestComponent
	@RestController
	static class TestController extends AdminReportController {
		TestController(ListReportsUseCase listReports) {
			super(listReports);
		}
	}

}
