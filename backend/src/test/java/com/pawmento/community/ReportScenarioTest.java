package com.pawmento.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;

import jakarta.servlet.http.Cookie;

// 시나리오는 docs/community/report.md에 있다. 사유 규칙의 경계(공백·길이·유형별)는 ReportTest가 확인하고,
// 여기서는 HTTP 응답과 저장 결과를 경로마다 대표 경우로 확인한다.
// 테스트마다 새 회원과 새 글을 쓰고 그 글의 신고만 세므로 다른 테스트의 데이터를 지우지 않는다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class ReportScenarioTest {

	private static final String PASSWORD = "correct-password";
	private static final String POSTS = "/api/v1/posts";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("#112-S1 다른 회원의 글을 SPAM으로 신고하면 201과 reportId를 받고 PENDING 신고와 스냅샷이 저장된다")
	void reportPost() throws Exception {
		Member a = loginAsNewMember();
		Member b = loginAsNewMember();
		Long postId = writePost(b, "산책 후기", "공원 한 바퀴");
		editPost(b, postId, "산책 후기", "공원 두 바퀴");

		MvcResult result = report(a, postId, """
				{"reasonType":"SPAM"}""").andExpect(status().isCreated())
			.andExpect(jsonPath("$.reportId").isNumber())
			.andReturn();

		Map<String, Object> row = reportRow(readLong(result, "$.reportId"));
		assertThat(row).containsEntry("post_id", postId)
			.containsEntry("reporter_id", a.id())
			.containsEntry("status", "PENDING")
			.containsEntry("reason_type", "SPAM")
			.containsEntry("snapshot_title", "산책 후기")
			.containsEntry("snapshot_body", "공원 두 바퀴")
			.containsEntry("snapshot_category_code", "LIFE")
			.containsEntry("snapshot_post_version", postVersion(postId));
		assertThat(row.get("reason_detail")).isNull();
		assertThat(row.get("snapshot_cleared_at")).isNull();
		assertThat(row.get("created_at")).isNotNull();
	}

	@Test
	@DisplayName("#112-S2 접수한 뒤 글을 고쳐도 신고의 스냅샷은 그대로다")
	void snapshotStaysAfterEdit() throws Exception {
		Member a = loginAsNewMember();
		Member b = loginAsNewMember();
		Long postId = writePost(b, "산책 후기", "공원 한 바퀴");
		Long version = postVersion(postId);
		Long reportId = reportId(a, postId, "SPAM");

		editPost(b, postId, "수정한 글", "고친 본문");

		assertThat(reportRow(reportId)).containsEntry("snapshot_title", "산책 후기")
			.containsEntry("snapshot_body", "공원 한 바퀴")
			.containsEntry("snapshot_post_version", version);
		assertThat(postVersion(postId)).isGreaterThan(version);
	}

	@Test
	@DisplayName("#112-S3 처리 대기 중 다시 신고하면 글을 고친 뒤에도 409이고, 다른 회원은 신고할 수 있다")
	void duplicatePendingReport() throws Exception {
		Member a = loginAsNewMember();
		Member b = loginAsNewMember();
		Member c = loginAsNewMember();
		Long postId = writePost(b, "산책 후기", "공원 한 바퀴");
		reportId(a, postId, "SPAM");

		expectDuplicate(report(a, postId, """
				{"reasonType":"ABUSE"}"""));
		editPost(b, postId, "수정한 글", "고친 본문");
		expectDuplicate(report(a, postId, """
				{"reasonType":"ABUSE"}"""));
		assertThat(reportCount(postId, a.id())).isEqualTo(1);

		reportId(c, postId, "SPAM");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM post_reports WHERE post_id = ? AND status = 'PENDING'", Integer.class, postId))
			.isEqualTo(2);
	}

	@Test
	@DisplayName("#112-S6 작성자가 자기 글을 신고하면 403이고 신고가 생기지 않는다")
	void ownPost() throws Exception {
		Member b = loginAsNewMember();
		Long postId = writePost(b, "산책 후기", "공원 한 바퀴");

		report(b, postId, """
				{"reasonType":"SPAM"}""").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"))
			.andExpect(jsonPath("$.message").value("내 글은 신고할 수 없어요."));
		assertThat(reportCount(postId, b.id())).isZero();
	}

	@Test
	@DisplayName("#112-S7 지운 글과 없는 글은 404이고, 로그인하지 않으면 401이다")
	void missingPostAndAnonymous() throws Exception {
		Member a = loginAsNewMember();
		Member b = loginAsNewMember();
		Long deleted = writePost(b, "지울 글", "본문");
		mockMvc.perform(delete(POSTS + "/" + deleted).cookie(b.cookie())).andExpect(status().isNoContent());

		for (long postId : new long[] {deleted, 999_999_999L}) {
			report(a, postId, """
					{"reasonType":"SPAM"}""").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		}
		assertThat(reportCount(deleted, a.id())).isZero();

		Long visible = writePost(b, "산책 후기", "본문");
		mockMvc.perform(post(POSTS + "/" + visible + "/reports").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"reasonType":"SPAM"}""")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("#112-S8 기타로 신고하면 앞뒤 공백을 지운 설명이 저장된다")
	void otherWithDetail() throws Exception {
		Member a = loginAsNewMember();
		Member b = loginAsNewMember();
		Long postId = writePost(b, "산책 후기", "본문");

		MvcResult result = report(a, postId, """
				{"reasonType":"OTHER","reasonDetail":"  사진 속 차량 번호가 보여요  "}""")
			.andExpect(status().isCreated())
			.andReturn();

		assertThat(reportRow(readLong(result, "$.reportId"))).containsEntry("reason_type", "OTHER")
			.containsEntry("reason_detail", "사진 속 차량 번호가 보여요");
	}

	@Test
	@DisplayName("#112-S9 유형이 없거나 틀리거나, 기타 설명이 비었거나, 다른 유형에 설명을 보내면 400이고 신고가 생기지 않는다")
	void invalidReason() throws Exception {
		Member a = loginAsNewMember();
		Member b = loginAsNewMember();
		Long postId = writePost(b, "산책 후기", "본문");

		for (String body : new String[] {"{}", """
				{"reasonType":"spam"}""", """
				{"reasonType":"UNKNOWN"}""", """
				{"reasonType":"OTHER","reasonDetail":"\\uFEFF "}""", """
				{"reasonType":"SPAM","reasonDetail":"광고예요"}"""}) {
			report(a, postId, body).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION"));
		}
		assertThat(reportCount(postId, a.id())).isZero();
	}

	private void expectDuplicate(ResultActions result) throws Exception {
		result.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andExpect(jsonPath("$.message").value("이미 신고한 게시글이에요."));
	}

	private record Member(Long id, Cookie cookie) {
	}

	private Member loginAsNewMember() throws Exception {
		String email = "community-report-" + UUID.randomUUID() + "@example.com";
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"%s","nickname":"보호자"}
				""".formatted(email, PASSWORD))).andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"%s"}
				""".formatted(email, PASSWORD))).andExpect(status().isOk()).andReturn();
		Cookie cookie = login.getResponse().getCookie(AccessTokenCookie.NAME);
		assertThat(cookie).isNotNull();
		return new Member(readLong(login, "$.id"), new Cookie(AccessTokenCookie.NAME, cookie.getValue()));
	}

	private Long writePost(Member author, String title, String body) throws Exception {
		MvcResult result = mockMvc.perform(post(POSTS).cookie(author.cookie())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"category":"LIFE","title":"%s","body":"%s"}
					""".formatted(title, body))).andExpect(status().isCreated()).andReturn();
		return readLong(result, "$.postId");
	}

	private void editPost(Member author, Long postId, String title, String body) throws Exception {
		mockMvc.perform(put(POSTS + "/" + postId).cookie(author.cookie())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"category":"LIFE","title":"%s","body":"%s"}
					""".formatted(title, body))).andExpect(status().isOk());
	}

	private ResultActions report(Member reporter, long postId, String json) throws Exception {
		return mockMvc.perform(post(POSTS + "/" + postId + "/reports").cookie(reporter.cookie())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private Long reportId(Member reporter, Long postId, String reasonType) throws Exception {
		return readLong(report(reporter, postId, """
				{"reasonType":"%s"}""".formatted(reasonType)).andExpect(status().isCreated()).andReturn(),
				"$.reportId");
	}

	private Map<String, Object> reportRow(Long reportId) {
		return jdbcTemplate.queryForMap("SELECT * FROM post_reports WHERE id = ?", reportId);
	}

	private int reportCount(Long postId, Long reporterId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_reports WHERE post_id = ? AND reporter_id = ?",
				Integer.class, postId, reporterId);
	}

	private Long postVersion(Long postId) {
		return jdbcTemplate.queryForObject("SELECT version FROM posts WHERE id = ?", Long.class, postId);
	}

	private static Long readLong(MvcResult result, String path) throws Exception {
		Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
		return value.longValue();
	}

}
