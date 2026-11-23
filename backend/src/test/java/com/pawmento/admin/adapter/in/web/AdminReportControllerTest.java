package com.pawmento.admin.adapter.in.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.admin.application.exception.ReportNotFoundException;
import com.pawmento.admin.application.exception.ReportProcessingConflictException;
import com.pawmento.admin.application.port.in.GetReportDetailUseCase;
import com.pawmento.admin.application.port.in.ListReportItemResult;
import com.pawmento.admin.application.port.in.ListReportItemResult.PostState;
import com.pawmento.admin.application.port.in.ListReportItemResult.ReasonType;
import com.pawmento.admin.application.port.in.ListReportsQuery;
import com.pawmento.admin.application.port.in.ListReportsUseCase;
import com.pawmento.admin.application.port.in.ProcessReportCommand;
import com.pawmento.admin.application.port.in.ProcessReportCommand.Resolution;
import com.pawmento.admin.application.port.in.ProcessReportUseCase;
import com.pawmento.admin.application.port.in.ReportDetailResult;
import com.pawmento.admin.application.port.in.ReportStatus;

@WebMvcTest(AdminReportControllerTest.TestController.class)
@Import({AdminReportControllerTest.TestController.class, AdminReportControllerTest.TestSecurity.class})
class AdminReportControllerTest {
	private static final String REPORTS = "/api/v1/admin/reports";
	private static final String PROCESS_BODY = """
			{"resolution":"DISMISSED","version":0,"memo":"확인 완료"}
			""";

	@Autowired MockMvc mvc;
	@MockitoBean ListReportsUseCase listReports;
	@MockitoBean GetReportDetailUseCase getReportDetail;
	@MockitoBean ProcessReportUseCase processReport;

	@Test
	@DisplayName("#92-S1 목록 요청의 기본값과 실제 MVC 날짜·커서 직렬화를 확인한다")
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
				.andExpect(jsonPath("$.items[0].postState").value("DELETED"));
	}

	@Test
	@DisplayName("#92-S2 삭제된 글의 신고 상세는 현재 상태와 함께 조회된다")
	void returnsReportForDeletedPost() throws Exception {
		when(getReportDetail.get(101L)).thenReturn(detail(ReportStatus.PENDING, 0, null));

		mvc.perform(get(REPORTS + "/101").with(actor("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reportId").value(101))
				.andExpect(jsonPath("$.target.state").value("DELETED"));
	}

	@Test
	@DisplayName("#92-S3 처리 요청은 인증된 관리자의 ID와 기대 버전을 전달한다")
	void passesAuthenticatedActorToUseCase() throws Exception {
		var command = new ProcessReportCommand(101, Resolution.DISMISSED, 0, "확인 완료");
		var processing = new ReportDetailResult.Processing(8, Instant.EPOCH, "확인 완료");
		when(processReport.process(command, 8)).thenReturn(detail(ReportStatus.DISMISSED, 1, processing));

		mvc.perform(patch(REPORTS + "/101").with(actor("ADMIN")).contentType(MediaType.APPLICATION_JSON)
						.content(PROCESS_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DISMISSED"))
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.processing.processedBy").value(8));
	}

	@Test
	@DisplayName("#92-S5 잘못된 목록 입력과 처리 버전은 공통 400 응답으로 변환된다")
	void mapsBadInputToValidationError() throws Exception {
		mvc.perform(get(REPORTS).param("size", "0").with(actor("ADMIN")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
		mvc.perform(get(REPORTS + "/0").with(actor("ADMIN")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
		mvc.perform(patch(REPORTS + "/101").with(actor("ADMIN")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"resolution\":\"DISMISSED\",\"version\":\"0\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));

		verifyNoInteractions(listReports, getReportDetail, processReport);
	}

	@Test
	@DisplayName("#92-S5 없는 신고는 공통 404 응답으로 변환된다")
	void mapsMissingReportToNotFound() throws Exception {
		when(getReportDetail.get(999L)).thenThrow(new ReportNotFoundException());

		mvc.perform(get(REPORTS + "/999").with(actor("ADMIN")))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("#92-S6 처리 충돌은 공통 409 응답으로 변환된다")
	void mapsWriteConflictToConflictResponse() throws Exception {
		when(processReport.process(new ProcessReportCommand(101, Resolution.DISMISSED, 0, "확인 완료"), 8))
				.thenThrow(new ReportProcessingConflictException("최신 신고 정보를 확인해 주세요."));

		mvc.perform(patch(REPORTS + "/101").with(actor("ADMIN")).contentType(MediaType.APPLICATION_JSON)
						.content(PROCESS_BODY))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	@DisplayName("#92-S4 관리자 matcher를 적용한 테스트 환경에서 비로그인과 일반 회원은 유스케이스에 접근하지 못한다")
	void rejectsRequestsOutsideAdminRoleWithTestFilter() throws Exception {
		for (var request : List.of(get(REPORTS), get(REPORTS + "/101"), patch(REPORTS + "/101"))) {
			mvc.perform(request).andExpect(status().isUnauthorized());
			mvc.perform(request.with(actor("USER"))).andExpect(status().isForbidden());
		}

		verifyNoInteractions(listReports, getReportDetail, processReport);
	}

	private static JwtRequestPostProcessor actor(String role) {
		return jwt().jwt(token -> token.subject("8")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

	private static ReportDetailResult detail(ReportStatus status, long version, ReportDetailResult.Processing processing) {
		return new ReportDetailResult(101, 201, status, ReasonType.PRIVACY, "신고 설명", Instant.EPOCH, version,
				null, new ReportDetailResult.Target(PostState.DELETED, null), processing);
	}

	@TestComponent
	@RestController
	static class TestController extends AdminReportController {
		TestController(ListReportsUseCase listReports, GetReportDetailUseCase getReportDetail,
				ProcessReportUseCase processReport) {
			super(listReports, getReportDetail, processReport);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestSecurity {
		@Bean
		SecurityFilterChain adminTestFilterChain(HttpSecurity http) throws Exception {
			return http.csrf(AbstractHttpConfigurer::disable)
					.authorizeHttpRequests(requests -> requests.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
							.anyRequest().denyAll())
					.exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
					.build();
		}
	}
}
