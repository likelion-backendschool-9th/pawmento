package com.pawmento.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;

import jakarta.servlet.http.Cookie;

// 시나리오는 docs/community/post.md에 있다.
// DB는 다른 테스트와 같이 쓴다. 글 개수를 세는 시나리오가 흔들리지 않게 테스트마다 글을 지우고, 카테고리는 남긴다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class PostScenarioTest {

	private static final String PASSWORD = "correct-password";
	private static final String POSTS = "/api/v1/posts";
	// 사용하지 않는 카테고리. 테스트에서만 넣는다
	private static final String HIDDEN = "TEST_HIDDEN";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtEncoder jwtEncoder;

	@BeforeEach
	void clearPosts() {
		// 신고가 글을 FK로 가리키므로 먼저 지운다
		jdbcTemplate.update("DELETE FROM post_reports");
		jdbcTemplate.update("DELETE FROM posts");
	}

	@Test
	@DisplayName("#21-S1 로그인한 회원이 글을 쓰면 201과 postId를 받고, 상세가 카테고리 이름과 작성자를 돌려준다")
	void createPost() throws Exception {
		Member a = loginAsNewMember();

		MvcResult created = createPost(a.cookie(), "CARE", "노견 관절 영양제 후기", "3개월 먹였어요")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.postId").isNumber())
			.andReturn();
		Long postId = readLong(created, "$.postId");

		mockMvc.perform(get(POSTS + "/" + postId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.postId").value(postId))
			.andExpect(jsonPath("$.category").value("CARE"))
			.andExpect(jsonPath("$.categoryName").value("건강·돌봄"))
			.andExpect(jsonPath("$.title").value("노견 관절 영양제 후기"))
			.andExpect(jsonPath("$.body").value("3개월 먹였어요"))
			.andExpect(jsonPath("$.authorId").value(a.id()));
	}

	@Test
	@DisplayName("#21-S2 제목이 공백이거나 101자면 400 VALIDATION이고 글이 늘지 않는다")
	void createPostWithInvalidTitle() throws Exception {
		Member a = loginAsNewMember();

		createPost(a.cookie(), "CARE", "   ", "본문").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));
		createPost(a.cookie(), "CARE", "가".repeat(101), "본문").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));
		// 줄바꿈 없는 공백만 있는 제목도 빈 제목이다 (프론트엔드 trim()과 같은 기준)
		createPost(a.cookie(), "CARE", "\u00A0\u00A0", "본문").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));
		// 제목과 본문이 함께 틀리면 요청 필드 순서대로 제목 오류가 나간다
		createPost(a.cookie(), "CARE", "가".repeat(101), "").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("제목은 1~100자로 입력해 주세요."));
		assertThat(postCount()).isZero();

		// 길이는 앞뒤 공백을 지운 뒤 센다
		createPost(a.cookie(), "CARE", " " + "가".repeat(100) + " ", "본문").andExpect(status().isCreated());
		// BOM만 있는 제목은 빈 제목이다. JSON 이스케이프로 보낸다
		createPost(a.cookie(), "CARE", "\\uFEFF", "본문").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("제목은 1~100자로 입력해 주세요."));
		// 프론트엔드 trim()이 남기는 제어 문자(U+001C~U+001F)만 있는 제목은 빈 제목이 아니다
		createPost(a.cookie(), "CARE", "\\u001C", "본문").andExpect(status().isCreated());
		createPost(a.cookie(), "CARE", "\\u001F", "본문").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("#21-S3 없는 카테고리는 400 VALIDATION, 사용하지 않는 카테고리는 409 CONFLICT이고 글이 늘지 않는다")
	void createPostWithInvalidCategory() throws Exception {
		Member a = loginAsNewMember();
		ensureHiddenCategory();

		createPost(a.cookie(), "NOPE", "제목", "본문").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));
		createPost(a.cookie(), HIDDEN, "제목", "본문").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));

		assertThat(postCount()).isZero();
	}

	@Test
	@DisplayName("#21-S4 최신 글부터 20개와 nextCursor를 받고, 그 커서로 나머지 5개와 nextCursor null을 받는다")
	void listWithCursor() throws Exception {
		Member a = loginAsNewMember();
		for (int i = 1; i <= 25; i++) {
			createPostId(a.cookie(), "LIFE", "글 " + i, "본문 " + i);
		}

		// size를 보내지 않으면 20개씩 준다
		MvcResult first = mockMvc.perform(get(POSTS).param("category", "LIFE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts.length()").value(20))
			.andExpect(jsonPath("$.posts[0].title").value("글 25"))
			.andExpect(jsonPath("$.posts[19].title").value("글 6"))
			.andExpect(jsonPath("$.nextCursor").isNumber())
			.andReturn();
		Long nextCursor = readLong(first, "$.nextCursor");

		mockMvc
			.perform(get(POSTS).param("category", "LIFE").param("size", "20").param("cursor", nextCursor.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts.length()").value(5))
			.andExpect(jsonPath("$.posts[0].title").value("글 5"))
			.andExpect(jsonPath("$.posts[4].title").value("글 1"))
			.andExpect(jsonPath("$.nextCursor").isEmpty());
	}

	@Test
	@DisplayName("#21-S5 카테고리를 골라 보면 그 카테고리 글만 받고 추모 글이 섞이지 않는다")
	void listIsSeparatedByCategory() throws Exception {
		Member a = loginAsNewMember();
		for (int i = 0; i < 3; i++) {
			createPostId(a.cookie(), "CARE", "돌봄 " + i, "본문");
		}
		for (int i = 0; i < 2; i++) {
			createPostId(a.cookie(), "LIFE", "생활 " + i, "본문");
		}
		createPostId(a.cookie(), "MEMORIAL", "추모", "본문");

		MvcResult result = mockMvc.perform(get(POSTS).param("category", "CARE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts.length()").value(3))
			.andReturn();
		List<String> categories = JsonPath.read(result.getResponse().getContentAsString(), "$.posts[*].category");
		assertThat(categories).containsOnly("CARE");
	}

	@Test
	@DisplayName("#21-S6 없는 카테고리로 목록을 부르거나 카테고리 없이 부르면 400 VALIDATION이다")
	void listWithInvalidCategory() throws Exception {
		mockMvc.perform(get(POSTS).param("category", "NOPE"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));

		mockMvc.perform(get(POSTS))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("카테고리를 선택해 주세요."));
	}

	@Test
	@DisplayName("#21-S13 size=0, size=51, cursor=0, size=abc이면 400 VALIDATION이다")
	void listOutOfRange() throws Exception {
		mockMvc.perform(get(POSTS).param("category", "LIFE").param("size", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("size는 1~50 사이로 보내 주세요."));
		mockMvc.perform(get(POSTS).param("category", "LIFE").param("size", "51"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("size는 1~50 사이로 보내 주세요."));
		mockMvc.perform(get(POSTS).param("category", "LIFE").param("cursor", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("cursor는 1 이상으로 보내 주세요."));
		mockMvc.perform(get(POSTS).param("category", "LIFE").param("size", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("size 값의 형식이 올바르지 않습니다."));
	}

	@Test
	@DisplayName("#21-S7 목록과 상세는 쿠키 없이도, 만료된 토큰 쿠키가 남아 있어도 200이다")
	void readWithoutLogin() throws Exception {
		Member a = loginAsNewMember();
		Long postId = createPostId(a.cookie(), "CARE", "제목", "본문");
		Cookie expired = new Cookie(AccessTokenCookie.NAME, expiredToken());

		mockMvc.perform(get(POSTS).param("category", "CARE")).andExpect(status().isOk());
		mockMvc.perform(get(POSTS + "/" + postId)).andExpect(status().isOk());

		mockMvc.perform(get(POSTS).param("category", "CARE").cookie(expired)).andExpect(status().isOk());
		mockMvc.perform(get(POSTS + "/" + postId).cookie(expired)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("#21-S7 공개 경로는 목록·카테고리 목록·숫자 id 상세뿐이라 /posts/me 같은 다른 GET은 쿠키 없이 401이다")
	void otherGetPathsNeedLogin() throws Exception {
		mockMvc.perform(get(POSTS + "/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(get(POSTS + "/1/comments")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("#21-S8 작성자가 고치면 200이고, 상세가 고친 값과 늦어진 updatedAt을 주며 목록 카테고리가 바뀐다")
	void editPost() throws Exception {
		Member a = loginAsNewMember();
		Long postId = createPostId(a.cookie(), "CARE", "제목", "본문");

		editPost(a.cookie(), postId, "MEMORIAL", "고친 제목", "고친 본문").andExpect(status().isOk())
			.andExpect(jsonPath("$.category").value("MEMORIAL"))
			.andExpect(jsonPath("$.title").value("고친 제목"));

		MvcResult detail = mockMvc.perform(get(POSTS + "/" + postId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.category").value("MEMORIAL"))
			.andExpect(jsonPath("$.categoryName").value("추모 방식"))
			.andExpect(jsonPath("$.title").value("고친 제목"))
			.andExpect(jsonPath("$.body").value("고친 본문"))
			.andReturn();
		Instant createdAt = readInstant(detail, "$.createdAt");
		Instant updatedAt = readInstant(detail, "$.updatedAt");
		assertThat(updatedAt).isAfter(createdAt);

		assertThat(listedIds("MEMORIAL")).contains(postId);
		assertThat(listedIds("CARE")).doesNotContain(postId);
	}

	@Test
	@DisplayName("#21-S9 작성자가 지우면 204이고, 상세는 404, 목록에 없으며 DB 행은 deleted_at이 채워진 채 남는다")
	void deletePost() throws Exception {
		Member a = loginAsNewMember();
		Long postId = createPostId(a.cookie(), "CARE", "제목", "본문");

		mockMvc.perform(delete(POSTS + "/" + postId).cookie(a.cookie())).andExpect(status().isNoContent());

		mockMvc.perform(get(POSTS + "/" + postId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		assertThat(listedIds("CARE")).doesNotContain(postId);
		Instant deletedAt = jdbcTemplate.queryForObject("SELECT deleted_at FROM posts WHERE id = ?", Instant.class,
				postId);
		assertThat(deletedAt).isNotNull();

		// 지운 글을 다시 지워도 404다
		mockMvc.perform(delete(POSTS + "/" + postId).cookie(a.cookie())).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("#21-S10 다른 회원이 고치거나 지우면 403 FORBIDDEN이고 글은 그대로 목록에 남는다")
	void otherMemberCannotEditOrDelete() throws Exception {
		Member a = loginAsNewMember();
		Member b = loginAsNewMember();
		Long postId = createPostId(a.cookie(), "CARE", "제목", "본문");

		editPost(b.cookie(), postId, "CARE", "남이 고친 제목", "남이 고친 본문").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(delete(POSTS + "/" + postId).cookie(b.cookie()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		// 없는 카테고리나 숨긴 카테고리를 보내도 남의 글이면 403이다
		ensureHiddenCategory();
		editPost(b.cookie(), postId, "NOPE", "남이 고친 제목", "남이 고친 본문").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		editPost(b.cookie(), postId, HIDDEN, "남이 고친 제목", "남이 고친 본문").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		mockMvc.perform(get(POSTS + "/" + postId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("제목"))
			.andExpect(jsonPath("$.body").value("본문"));
		assertThat(listedIds("CARE")).contains(postId);
	}

	@Test
	@DisplayName("#21-S11 쿠키 없이 쓰기·고치기·지우기를 부르면 401이다")
	void writeWithoutLogin() throws Exception {
		mockMvc.perform(post(POSTS).contentType(MediaType.APPLICATION_JSON).content(postJson("CARE", "제목", "본문")))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(put(POSTS + "/1").contentType(MediaType.APPLICATION_JSON).content(postJson("CARE", "제목", "본문")))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(POSTS + "/1")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("#21-S12 쿠키 없이 카테고리 목록을 부르면 처음 카테고리 5개를 순서대로 code와 name으로 받는다")
	void listCategories() throws Exception {
		mockMvc.perform(get(POSTS + "/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(5))
			.andExpect(jsonPath("$[*].code").value(contains("LIFE", "CARE", "FUNERAL", "MEMORIAL",
					"MEMORY")))
			.andExpect(jsonPath("$[*].name").value(contains("반려 생활", "건강·돌봄", "장례 정보",
					"추모 방식", "기억 나누기")));
	}

	private record Member(long id, Cookie cookie) {
	}

	private Member loginAsNewMember() throws Exception {
		String email = "community-" + UUID.randomUUID() + "@example.com";
		mockMvc
			.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s","nickname":"보호자"}
						""".formatted(email, PASSWORD)))
			.andExpect(status().isCreated());
		MvcResult login = mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		Cookie cookie = login.getResponse().getCookie(AccessTokenCookie.NAME);
		assertThat(cookie).isNotNull();
		return new Member(readLong(login, "$.id"), new Cookie(AccessTokenCookie.NAME, cookie.getValue()));
	}

	private ResultActions createPost(Cookie cookie, String category, String title, String body) throws Exception {
		return mockMvc.perform(post(POSTS).cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content(postJson(category, title, body)));
	}

	private Long createPostId(Cookie cookie, String category, String title, String body) throws Exception {
		return readLong(createPost(cookie, category, title, body).andExpect(status().isCreated()).andReturn(),
				"$.postId");
	}

	private ResultActions editPost(Cookie cookie, Long postId, String category, String title, String body)
			throws Exception {
		return mockMvc.perform(put(POSTS + "/" + postId).cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content(postJson(category, title, body)));
	}

	private static String postJson(String category, String title, String body) {
		return """
				{"category":"%s","title":"%s","body":"%s"}
				""".formatted(category, title, body);
	}

	private List<Long> listedIds(String category) throws Exception {
		MvcResult result = mockMvc.perform(get(POSTS).param("category", category).param("size", "50"))
			.andExpect(status().isOk())
			.andReturn();
		List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.posts[*].postId");
		return ids.stream().map(Number::longValue).toList();
	}

	private int postCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM posts", Integer.class);
	}

	private void ensureHiddenCategory() {
		jdbcTemplate.update("""
				INSERT INTO post_categories (code, name, sort_order, active)
				SELECT ?, '숨긴 카테고리', 99, false
				WHERE NOT EXISTS (SELECT 1 FROM post_categories WHERE code = ?)
				""", HIDDEN, HIDDEN);
	}

	// 서명은 맞지만 유효 시간이 지난 토큰. 검증의 허용 오차(60초)보다 충분히 이전으로 둔다
	private String expiredToken() {
		Instant issuedAt = Instant.now().minus(2, ChronoUnit.HOURS);
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject("1")
			.issuedAt(issuedAt)
			.expiresAt(issuedAt.plus(1, ChronoUnit.HOURS))
			.claim("role", "USER")
			.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}

	private static Long readLong(MvcResult result, String path) throws Exception {
		Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
		return value.longValue();
	}

	private static Instant readInstant(MvcResult result, String path) throws Exception {
		String value = JsonPath.read(result.getResponse().getContentAsString(), path);
		return Instant.parse(value);
	}

}
