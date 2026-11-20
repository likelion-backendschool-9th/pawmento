package com.pawmento.member.adapter.out.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.pawmento.member.application.exception.SocialLoginFailedException;
import com.pawmento.member.application.port.out.SocialProfile;
import com.pawmento.member.domain.AuthProvider;

// 시나리오는 docs/member/social-login.md에 있다. 카카오는 가짜 서버로 대신한다
class KakaoProfileClientTest {

	private static final String TOKEN_URI = "http://kakao.test/token";

	private static final String USER_INFO_URI = "http://kakao.test/me";

	private static final String REDIRECT_URI = "http://localhost:3000/oauth/callback/kakao";

	private static final String TOKEN_JSON = """
		{"access_token": "kakao-access-token", "token_type": "bearer", "expires_in": 21599}
		""";

	private record Fixture(KakaoProfileClient client, MockRestServiceServer server) {
	}

	private static Fixture fixture(String clientId, String clientSecret) {
		KakaoOAuthProperties properties = new KakaoOAuthProperties(clientId, clientSecret, TOKEN_URI, USER_INFO_URI,
				Duration.ofSeconds(2), Duration.ofSeconds(3));
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		return new Fixture(new KakaoProfileClient(properties, builder), server);
	}

	private static String userJson(boolean valid, boolean verified) {
		return """
			{"id": 4242424242, "connected_at": "2026-11-19T05:00:00Z",
			 "kakao_account": {"has_email": true, "email_needs_agreement": false,
			   "is_email_valid": %s, "is_email_verified": %s, "email": "dawit@kakao.com",
			   "profile": {"nickname": "다윗", "is_default_image": true}}}
			""".formatted(valid, verified);
	}

	@Test
	@DisplayName("#40-S11 code를 토큰으로 바꾸고 카카오 사용자 정보로 프로필을 만든다. 숫자 id는 문자열로 바꾼다")
	void loadsProfile() {
		Fixture f = fixture("rest-api-key", "kakao-secret");
		f.server().expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().formDataContains(Map.of(
					"grant_type", "authorization_code",
					"client_id", "rest-api-key",
					"client_secret", "kakao-secret",
					"redirect_uri", REDIRECT_URI,
					"code", "abc")))
			.andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
		f.server().expect(requestTo(USER_INFO_URI))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("Authorization", "Bearer kakao-access-token"))
			.andRespond(withSuccess(userJson(true, true), MediaType.APPLICATION_JSON));

		SocialProfile profile = f.client().load("abc", null, REDIRECT_URI);

		assertThat(profile).isEqualTo(new SocialProfile(AuthProvider.KAKAO, "4242424242", "dawit@kakao.com", "다윗"));
		f.server().verify();
	}

    @Test
	@DisplayName("#40-S11 Client Secret을 설정하지 않으면 토큰 교환에 client_secret을 보내지 않는다")
	void noClientSecretIsNotSent() {
		Fixture f = fixture("rest-api-key", "");
		f.server().expect(requestTo(TOKEN_URI))
			.andExpect(request -> assertThat(((org.springframework.mock.http.client.MockClientHttpRequest) request)
				.getBodyAsString()).doesNotContain("client_secret"))
			.andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
		f.server().expect(requestTo(USER_INFO_URI))
			.andRespond(withSuccess(userJson(true, true), MediaType.APPLICATION_JSON));

		f.client().load("abc", null, REDIRECT_URI);
		f.server().verify();
	}

	@Test
	@DisplayName("#40-S11 이메일이 유효하지 않거나 인증되지 않았으면 이메일을 쓰지 않는다")
	void unverifiedEmailIsDropped() {
		boolean[][] cases = { { false, true }, { true, false } };
		for (boolean[] c : cases) {
			Fixture f = fixture("rest-api-key", "");
			f.server().expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
			f.server().expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess(userJson(c[0], c[1]), MediaType.APPLICATION_JSON));

			assertThat(f.client().load("abc", null, REDIRECT_URI).email()).isNull();
		}
	}

	@Test
	@DisplayName("#40-S11 이메일 동의를 하지 않아 kakao_account에 이메일이 없으면 이메일 없이 프로필을 만든다")
	void noEmailAgreement() {
		Fixture f = fixture("rest-api-key", "");
		f.server().expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
		f.server().expect(requestTo(USER_INFO_URI))
			.andRespond(withSuccess("{\"id\": 1, \"kakao_account\": {\"has_email\": false}}", MediaType.APPLICATION_JSON));

		SocialProfile profile = f.client().load("abc", null, REDIRECT_URI);

		assertThat(profile.providerUserId()).isEqualTo("1");
		assertThat(profile.email()).isNull();
		assertThat(profile.nickname()).isNull();
	}

	@Test
	@DisplayName("#40-S7 카카오가 code를 거절하면 로그인 실패다")
	void rejectedCodeFails() {
		Fixture f = fixture("rest-api-key", "");
		f.server().expect(requestTo(TOKEN_URI)).andRespond(withBadRequest()
			.body("{\"error\": \"invalid_grant\"}").contentType(MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> f.client().load("expired", null, REDIRECT_URI))
			.isInstanceOf(SocialLoginFailedException.class);
		f.server().verify();
	}

	@Test
	@DisplayName("#40-S13 REST API 키가 없으면 카카오를 부르지 않고 로그인 실패다")
	void noCredentialsFailsWithoutCall() {
		Fixture f = fixture("", "");

		assertThatThrownBy(() -> f.client().load("abc", null, REDIRECT_URI))
			.isInstanceOf(SocialLoginFailedException.class);
		f.server().verify();
	}

}