package com.pawmento.member.adapter.out.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
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

// 시나리오는 docs/member/social-login.md에 있다. 구글은 가짜 서버로 대신하고, 진짜 키로 하는 호출(S14)은 자동 테스트에 넣지 않는다
class GoogleProfileClientTest {

	private static final String TOKEN_URI = "http://google.test/token";

	private static final String USER_INFO_URI = "http://google.test/userinfo";

	private static final String REDIRECT_URI = "http://localhost:3000/auth/callback/google";

	private static final String TOKEN_JSON = """
		{"access_token": "google-access-token", "expires_in": 3599, "token_type": "Bearer"}
		""";

	private record Fixture(GoogleProfileClient client, MockRestServiceServer server) {
	}

	private static Fixture fixture(String clientId, String clientSecret) {
		GoogleOAuthProperties properties = new GoogleOAuthProperties(clientId, clientSecret, TOKEN_URI, USER_INFO_URI,
				Duration.ofSeconds(2), Duration.ofSeconds(3));
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		return new Fixture(new GoogleProfileClient(properties, builder), server);
	}

	private static String userJson(boolean emailVerified) {
		return """
			{"sub": "g-123", "email": "dawit@gmail.com", "email_verified": %s, "name": "다윗", "picture": "https://..."}
			""".formatted(emailVerified);
	}

	@Test
	@DisplayName("#40-S1 code를 토큰으로 바꾸고, 그 토큰으로 사용자 정보를 받아 프로필을 만든다")
	void loadsProfile() {
		Fixture f = fixture("client-id", "client-secret");
		f.server().expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
			.andExpect(content().formDataContains(Map.of(
					"grant_type", "authorization_code",
					"code", "abc",
					"client_id", "client-id",
					"client_secret", "client-secret",
					"redirect_uri", REDIRECT_URI)))
			.andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
		f.server().expect(requestTo(USER_INFO_URI))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("Authorization", "Bearer google-access-token"))
			.andRespond(withSuccess(userJson(true), MediaType.APPLICATION_JSON));

		SocialProfile profile = f.client().load("abc", null, REDIRECT_URI);

		assertThat(profile).isEqualTo(new SocialProfile(AuthProvider.GOOGLE, "g-123", "dawit@gmail.com", "다윗"));
		f.server().verify();
	}

	@Test
	@DisplayName("#40-S8 구글이 확인하지 않은 이메일은 쓰지 않는다")
	void unverifiedEmailIsDropped() {
		Fixture f = fixture("client-id", "client-secret");
		f.server().expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
		f.server().expect(requestTo(USER_INFO_URI))
			.andRespond(withSuccess(userJson(false), MediaType.APPLICATION_JSON));

		SocialProfile profile = f.client().load("abc", null, REDIRECT_URI);

		assertThat(profile.providerUserId()).isEqualTo("g-123");
		assertThat(profile.email()).isNull();
	}

	@Test
	@DisplayName("#40-S7 구글이 code를 거절하면 로그인 실패이고 사용자 정보는 부르지 않는다")
	void rejectedCodeFails() {
		Fixture f = fixture("client-id", "client-secret");
		f.server().expect(requestTo(TOKEN_URI)).andRespond(withBadRequest()
			.body("{\"error\": \"invalid_grant\"}").contentType(MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> f.client().load("expired", null, REDIRECT_URI))
			.isInstanceOf(SocialLoginFailedException.class)
			.hasMessageNotContaining("client-secret");
		f.server().verify();
	}

	@Test
	@DisplayName("#40-S7 사용자 정보 요청에서 구글 장애가 나도 로그인 실패로 바꾼다")
	void userInfoErrorFails() {
		Fixture f = fixture("client-id", "client-secret");
		f.server().expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
		f.server().expect(requestTo(USER_INFO_URI)).andRespond(withServerError());

		assertThatThrownBy(() -> f.client().load("abc", null, REDIRECT_URI))
			.isInstanceOf(SocialLoginFailedException.class);
	}

	@Test
	@DisplayName("#40-S7 토큰 응답에 access_token이 없거나 사용자 정보에 sub가 없으면 로그인 실패다")
	void missingFieldsFail() {
		Fixture noToken = fixture("client-id", "client-secret");
		noToken.server().expect(requestTo(TOKEN_URI)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> noToken.client().load("abc", null, REDIRECT_URI))
			.isInstanceOf(SocialLoginFailedException.class);

		Fixture noSub = fixture("client-id", "client-secret");
		noSub.server().expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
		noSub.server().expect(requestTo(USER_INFO_URI))
			.andRespond(withSuccess("{\"email\": \"dawit@gmail.com\"}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> noSub.client().load("abc", null, REDIRECT_URI))
			.isInstanceOf(SocialLoginFailedException.class);
	}

	@Test
	@DisplayName("#40-S13 키가 설정되지 않으면 구글을 부르지 않고 로그인 실패다")
	void noCredentialsFailsWithoutCall() {
		Fixture f = fixture("", "");

		assertThatThrownBy(() -> f.client().load("abc", null, REDIRECT_URI))
			.isInstanceOf(SocialLoginFailedException.class);
		f.server().verify();
	}

}