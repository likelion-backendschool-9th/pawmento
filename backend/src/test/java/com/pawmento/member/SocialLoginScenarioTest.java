package com.pawmento.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.exception.DuplicateSocialAccountException;
import com.pawmento.member.application.exception.SocialLoginFailedException;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.application.port.out.LoadSocialProfilePort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.application.port.out.SocialProfile;
import com.pawmento.member.domain.AuthProvider;
import com.pawmento.member.domain.Member;
import com.pawmento.member.application.service.MemberTagGenerator;
import com.pawmento.support.SignUpTokens;

import jakarta.servlet.http.Cookie;

// 시나리오는 docs/member/social-login.md에 있다.
// 제공자(구글·카카오·네이버)만 가짜로 바꾸고 HTTP·보안·DB는 실제로 확인한다. 진짜 구글 호출(S14)은 넣지 않는다
@Import({ TestcontainersConfiguration.class, SocialLoginScenarioTest.FakeSocialConfig.class })
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class SocialLoginScenarioTest {

	private static final String REDIRECT_URI = "http://localhost:3000/auth/callback/google";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SignUpTokens signUpTokens;

	@Autowired
	private FakeSocialProfiles socialProfiles;

	@Autowired
	private LoadMemberPort loadMemberPort;

	@Autowired
	private SaveMemberPort saveMemberPort;

	@Autowired
    private MemberTagGenerator tags;

	@TestConfiguration
	static class FakeSocialConfig {

		@Bean
		@Primary
		FakeSocialProfiles fakeSocialProfiles() {
			return new FakeSocialProfiles();
		}

	}

	// code마다 돌려줄 프로필을 미리 정해 둔다. 정해 두지 않은 code는 제공자가 거절한 것으로 본다
	static class FakeSocialProfiles implements LoadSocialProfilePort {

		private final Map<String, SocialProfile> byCode = new ConcurrentHashMap<>();

		void willReturn(String code, SocialProfile profile) {
			byCode.put(code, profile);
		}

		@Override
		public SocialProfile load(AuthProvider provider, String code, String state, String redirectUri) {
			SocialProfile profile = byCode.get(code);
			if (profile == null) {
				throw new SocialLoginFailedException("구글 로그인에 실패했습니다. 다시 시도해 주세요.");
			}
			return profile;
		}

	}

	@Test
	@DisplayName("#40-S1 처음 구글 로그인하면 가입되고, 받은 쿠키로 내 정보를 조회할 수 있다")
	void firstGoogleLogin() throws Exception {
		String email = uniqueEmail();
		String sub = uniqueSub();
		String code = google(sub, email, "다윗");

		MvcResult result = socialLogin("google", code).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.nickname").value("다윗"))
			.andExpect(jsonPath("$.tag").value(matchesPattern("\\d{4}")))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.accessToken").doesNotExist())
			.andExpect(cookie().httpOnly(AccessTokenCookie.NAME, true))
			.andExpect(cookie().sameSite(AccessTokenCookie.NAME, "Lax"))
			.andReturn();

		mockMvc.perform(get("/api/v1/members/me").cookie(accessTokenCookie(result)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email));

		Member saved = loadMemberPort.loadByEmail(email).orElseThrow();
		assertThat(loadMemberPort.loadByProvider(AuthProvider.GOOGLE, sub)).map(Member::getId).contains(saved.getId());
		assertThat(saved.getPasswordHash()).isNull();
	}

	@Test
	@DisplayName("#40-S3 같은 구글 계정으로 다시 로그인하면 같은 회원이다")
	void returningGoogleLogin() throws Exception {
		String code = google(uniqueSub(), uniqueEmail(), "다윗");

		Integer firstId = idOf(socialLogin("google", code).andExpect(status().isOk()).andReturn());
		Integer secondId = idOf(socialLogin("google", code).andExpect(status().isOk()).andReturn());

		assertThat(secondId).isEqualTo(firstId);
	}

	@Test
	@DisplayName("#40-S5 이메일로 가입한 회원과 같은 이메일(대소문자만 달라도)의 구글 계정은 409이고, 쿠키를 받지 못하며 소셜 계정도 연결되지 않는다")
	void sameEmailAsLocalIsConflict() throws Exception {
		String email = uniqueEmail();
		signUp(email).andExpect(status().isCreated());

		String sub = uniqueSub();

		// 구글이 대소문자를 다르게 준 같은 이메일도 같은 이메일로 본다
		socialLogin("google", google(sub, email.toUpperCase(Locale.ROOT), "다윗")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("처음 가입한 방법으로 로그인해 주세요")))
			.andExpect(cookie().doesNotExist(AccessTokenCookie.NAME));
		// 거절된 구글 계정은 기존 회원에 연결되지 않는다
		assertThat(loadMemberPort.loadByProvider(AuthProvider.GOOGLE, sub)).isEmpty();
	}

	@Test
	@DisplayName("#40-S6 구글로 가입한 이메일로는 이메일 가입을 할 수 없다")
	void localSignUpWithGoogleEmailIsConflict() throws Exception {
		String email = uniqueEmail();
		socialLogin("google", google(uniqueSub(), email, "다윗")).andExpect(status().isOk());

		signUp(email).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	@DisplayName("#40-S7 제공자가 code를 거절하면 401이고 쿠키를 받지 못한다")
	void rejectedCodeIsUnauthorized() throws Exception {
		socialLogin("google", "unknown-code").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(cookie().doesNotExist(AccessTokenCookie.NAME));
	}

	@Test
	@DisplayName("#40-S9 지원하지 않는 제공자와 local은 400이다")
	void unsupportedProviderIsBadRequest() throws Exception {
		String code = google(uniqueSub(), uniqueEmail(), "다윗");

		for (String provider : new String[] { "facebook", "local" }) {
			socialLogin(provider, code).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION"));
		}
	}

	@Test
	@DisplayName("#40-S9 code나 redirectUri가 없으면 400이다")
	void missingFieldsIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/auth/oauth/google").contentType(MediaType.APPLICATION_JSON)
				.content("{\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));
	}

	@Test
	@DisplayName("#40-S10 소셜 회원은 비밀번호 로그인을 할 수 없고, 더미 비교 문자열로도 401이다")
	void socialMemberCannotUsePasswordLogin() throws Exception {
		String email = uniqueEmail();
		socialLogin("google", google(uniqueSub(), email, "다윗")).andExpect(status().isOk());

		for (String password : new String[] { "wrong-password", "absent-member-password" }) {
			login(email, password).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(cookie().doesNotExist(AccessTokenCookie.NAME));
		}
	}

	@Test
	@DisplayName("#40-S15 #40-S16 DB 제약은 같은 소셜 계정과 같은 이메일을 각각 다른 예외로 알려준다")
	void uniqueConstraintsAreTranslated() {
		String sub = uniqueSub();
		String email = uniqueEmail();
		saveMemberPort.saveSocial(Member.registerSocial(email, "다윗",  tags.next("다윗"), Instant.now()), AuthProvider.GOOGLE, sub);

		assertThatThrownBy(() -> saveMemberPort
			.saveSocial(Member.registerSocial(uniqueEmail(), "다윗",  tags.next("다윗"), Instant.now()), AuthProvider.GOOGLE, sub))
			.isInstanceOf(DuplicateSocialAccountException.class);
		assertThatThrownBy(() -> saveMemberPort
			.saveSocial(Member.registerSocial(email, "다윗",  tags.next("다윗"), Instant.now()), AuthProvider.KAKAO, uniqueSub()))
			.isInstanceOf(DuplicateEmailException.class);
	}

	// code 하나에 구글 프로필 하나를 정해 두고 그 code를 돌려준다
	private String google(String sub, String email, String name) {
		String code = "code-" + UUID.randomUUID();
		socialProfiles.willReturn(code, new SocialProfile(AuthProvider.GOOGLE, sub, email, name));
		return code;
	}

	private ResultActions socialLogin(String provider, String code) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/oauth/" + provider).contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"%s\",\"redirectUri\":\"%s\"}".formatted(code, REDIRECT_URI)));
	}

	private ResultActions signUp(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"correct-password\",\"nickname\":\"보호자\",\"verificationToken\":\"%s\"}"
				.formatted(email, signUpTokens.issue(email))));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private static Integer idOf(MvcResult result) throws Exception {
		return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private static Cookie accessTokenCookie(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie(AccessTokenCookie.NAME);
		assertThat(cookie).isNotNull();
		return new Cookie(AccessTokenCookie.NAME, cookie.getValue());
	}

	// 테스트끼리 같은 DB를 쓰므로 이메일과 sub가 겹치지 않게 한다
	private static String uniqueEmail() {
		return "social-" + UUID.randomUUID() + "@example.com";
	}

	private static String uniqueSub() {
		return "g-" + UUID.randomUUID();
	}

}