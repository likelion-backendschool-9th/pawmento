package com.pawmento.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.domain.Member;
import com.pawmento.member.application.service.MemberTagGenerator;

import jakarta.servlet.http.Cookie;

// 시나리오는 docs/member/auth-base.md에 있다.
// 서명 키는 테스트 전용 값이다. 실제 환경의 키로 쓰지 않는다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class AuthScenarioTest {

	private static final String PASSWORD = "correct-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private SaveMemberPort memberRepository;

	@Autowired
    private MemberTagGenerator tags;

	@Test
	@DisplayName("#14-S1 가입하고 로그인하면 쿠키로 내 정보를 조회할 수 있다")
	void signUpLoginAndGetMe() throws Exception {
		String email = uniqueEmail();

		signUp(email, PASSWORD, "보리 보호자").andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.nickname").value("보리 보호자"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		MvcResult login = login(email, PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			// 토큰은 본문에 싣지 않는다
			.andExpect(jsonPath("$.accessToken").doesNotExist())
			.andExpect(cookie().httpOnly(AccessTokenCookie.NAME, true))
			.andExpect(cookie().sameSite(AccessTokenCookie.NAME, "Lax"))
			.andExpect(cookie().path(AccessTokenCookie.NAME, "/"))
			.andExpect(cookie().maxAge(AccessTokenCookie.NAME, 3600))
			.andReturn();

		mockMvc.perform(get("/api/v1/members/me").cookie(accessTokenCookie(login)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.nickname").value("보리 보호자"))
			.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	@DisplayName("#14-S2 로그인 없이 보호 API를 부르면 401과 JSON 에러를 받는다")
	void protectedApiWithoutLogin() throws Exception {
		mockMvc.perform(get("/api/v1/members/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8"))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
			// 기본 로그인 페이지로 넘기지 않는다
			.andExpect(header().doesNotExist(HttpHeaders.LOCATION));

		// 아직 없는 경로도 공개 경로가 아니면 로그인이 필요하다
		mockMvc.perform(get("/api/v1/pets")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("#14-S3 비밀번호가 틀리거나 없는 이메일이면 로그인에 실패하고 쿠키를 받지 못한다")
	void loginWithWrongCredentials() throws Exception {
		String email = uniqueEmail();
		signUp(email, PASSWORD, "보호자").andExpect(status().isCreated());

		login(email, "wrong-password").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
			.andExpect(cookie().doesNotExist(AccessTokenCookie.NAME));

		// 가입된 이메일인지 알 수 없도록 같은 응답을 준다
		login(uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
			.andExpect(cookie().doesNotExist(AccessTokenCookie.NAME));

		// BCrypt 한도(72바이트)를 넘는 비밀번호도 오류가 아니라 로그인 실패다
		login(email, "x".repeat(100)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("#14-S4 이미 가입된 이메일은 대소문자·공백이 달라도 가입할 수 없다")
	void signUpWithDuplicateEmail() throws Exception {
		String email = uniqueEmail();
		signUp(email, PASSWORD, "보호자").andExpect(status().isCreated());

		signUp(email.toUpperCase(), PASSWORD, "다른 보호자").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));

		// 가입할 때와 대소문자가 달라도 로그인된다
		login(email.toUpperCase(), PASSWORD).andExpect(status().isOk());
	}

	@Test
	@DisplayName("#14-S4 중복 확인을 통과한 뒤 저장에서 이메일이 겹쳐도 중복으로 처리한다")
	void saveDuplicateEmailAfterCheck() {
		// 두 가입 요청이 동시에 중복 확인을 통과한 상황. 저장소가 유니크 제약 위반을 중복 가입으로 바꿔 준다
		String email = uniqueEmail();
		memberRepository.save(Member.register(email, "hash", "먼저 가입", tags.next("먼저 가입"), Instant.now()));

		assertThatThrownBy(() -> memberRepository.save(Member.register(email, "hash", "나중 가입", tags.next("나중 가입"), Instant.now())))
			.isInstanceOf(DuplicateEmailException.class);
	}

	@Test
	@DisplayName("#14-S5 만료되거나 위조된 토큰으로는 보호 API를 쓸 수 없고, 그 쿠키가 남아 있어도 다시 로그인할 수 있다")
	void expiredOrForgedToken() throws Exception {
		String email = uniqueEmail();
		signUp(email, PASSWORD, "보호자").andExpect(status().isCreated());
		Cookie expired = new Cookie(AccessTokenCookie.NAME, expiredToken());
		Cookie forged = new Cookie(AccessTokenCookie.NAME, forgedToken(login(email, PASSWORD).andReturn()));

		mockMvc.perform(get("/api/v1/members/me").cookie(expired))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/api/v1/members/me").cookie(forged))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc
			.perform(post("/api/v1/auth/login").cookie(expired)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(email, PASSWORD, null)))
			.andExpect(status().isOk())
			.andExpect(cookie().exists(AccessTokenCookie.NAME));
	}

	@Test
	@DisplayName("#14-S6 로그아웃하면 토큰 쿠키가 지워진다")
	void logout() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isNoContent())
			.andExpect(cookie().value(AccessTokenCookie.NAME, ""))
			.andExpect(cookie().maxAge(AccessTokenCookie.NAME, 0))
			.andExpect(cookie().path(AccessTokenCookie.NAME, "/"))
			.andExpect(cookie().httpOnly(AccessTokenCookie.NAME, true));
	}

	@Test
	@DisplayName("#14-S6 다른 사이트의 폼 요청으로는 로그아웃되지 않는다")
	void logoutByCrossSiteForm() throws Exception {
		// HTML 폼이 보낼 수 있는 형식 세 가지와 Content-Type이 없는 요청. 쿠키를 지우는 응답이 나가면 안 된다
		for (MediaType formType : List.of(MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA,
				MediaType.TEXT_PLAIN)) {
			mockMvc
				.perform(post("/api/v1/auth/logout").header(HttpHeaders.ORIGIN, "https://other-site.example")
					.contentType(formType))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(cookie().doesNotExist(AccessTokenCookie.NAME));
		}
		mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.ORIGIN, "https://other-site.example"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(cookie().doesNotExist(AccessTokenCookie.NAME));
	}

	@Test
	@DisplayName("#14-S7 가입 입력이 규칙에 맞지 않으면 400과 이유를 받는다")
	void signUpWithInvalidInput() throws Exception {
		signUp("not-an-email", PASSWORD, "보호자").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("이메일 주소를 확인해 주세요."));

		signUp(uniqueEmail(), "short", "보호자").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("비밀번호는 8~72자로 입력해 주세요."));

		signUp(uniqueEmail(), PASSWORD, "가".repeat(21)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("닉네임은 1~20자로 입력해 주세요."));

		// 72자 이내지만 72바이트를 넘는 한글 비밀번호
		signUp(uniqueEmail(), "가".repeat(25), "보호자").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("비밀번호가 너무 깁니다. 한글은 24자까지 쓸 수 있습니다."));

		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));

		// 여러 항목이 틀리면 본문에 선언된 순서(이메일, 비밀번호, 닉네임)로 가장 앞 항목의 이유를 받는다.
		// 검증 순서가 실행마다 달라지지 않는지 보려고 여러 번 부른다
		for (int i = 0; i < 10; i++) {
			signUp("not-an-email", "short", "").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이메일 주소를 확인해 주세요."));
			signUp(uniqueEmail(), "short", "").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("비밀번호는 8~72자로 입력해 주세요."));
			// 바이트 제한도 비밀번호의 오류다. 뒤 항목인 닉네임보다 먼저 받는다
			signUp(uniqueEmail(), "가".repeat(25), "").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("비밀번호가 너무 깁니다. 한글은 24자까지 쓸 수 있습니다."));
		}
	}

	private ResultActions signUp(String email, String password, String nickname) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
			.content(json(email, password, nickname)));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(email, password, null)));
	}

	private static String json(String email, String password, String nickname) {
		String body = "\"email\":\"%s\",\"password\":\"%s\"".formatted(email, password);
		if (nickname != null) {
			body += ",\"nickname\":\"%s\"".formatted(nickname);
		}
		return "{" + body + "}";
	}

	// 테스트끼리 같은 DB를 쓰므로 이메일이 겹치지 않게 한다
	private static String uniqueEmail() {
		return "guardian-" + UUID.randomUUID() + "@example.com";
	}

	private static Cookie accessTokenCookie(MvcResult loginResult) {
		Cookie cookie = loginResult.getResponse().getCookie(AccessTokenCookie.NAME);
		assertThat(cookie).isNotNull();
		return new Cookie(AccessTokenCookie.NAME, cookie.getValue());
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

	// 정상 토큰의 서명 첫 글자를 바꿔 서명 검증에 실패하게 한다
	private static String forgedToken(MvcResult loginResult) {
		String token = accessTokenCookie(loginResult).getValue();
		int signatureStart = token.lastIndexOf('.') + 1;
		char replaced = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
		return token.substring(0, signatureStart) + replaced + token.substring(signatureStart + 1);
	}

}
