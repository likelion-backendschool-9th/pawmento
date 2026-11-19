package com.pawmento.member.adapter.out.oauth;

import java.net.http.HttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pawmento.member.application.exception.SocialLoginFailedException;
import com.pawmento.member.application.port.out.SocialProfile;
import com.pawmento.member.domain.AuthProvider;

@Component
class KakaoProfileClient implements SocialProfileClient {

	private static final Logger log = LoggerFactory.getLogger(KakaoProfileClient.class);

	private final KakaoOAuthProperties properties;

	private final RestClient restClient;

	@Autowired
	KakaoProfileClient(KakaoOAuthProperties properties) {
		this(properties, builderWithTimeouts(properties));
	}

	KakaoProfileClient(KakaoOAuthProperties properties, RestClient.Builder builder) {
		this.properties = properties;
		this.restClient = builder.build();
	}

	private static RestClient.Builder builderWithTimeouts(KakaoOAuthProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder().requestFactory(requestFactory);
	}

	@Override
	public AuthProvider provider() {
		return AuthProvider.KAKAO;
	}

	@Override
	public SocialProfile load(String code, String state, String redirectUri) {
		if (!properties.hasCredentials()) {
			log.warn("카카오 OAuth 키가 설정되지 않아 카카오 로그인을 할 수 없습니다.");
			throw new SocialLoginFailedException("지금은 카카오 로그인을 사용할 수 없습니다.");
		}
		try {
			String accessToken = exchangeToken(code, redirectUri);
			KakaoUser user = loadUser(accessToken);
			if (user == null || user.id() == null) {
				throw new SocialLoginFailedException("카카오 사용자 정보를 받지 못했습니다.");
			}
			KakaoAccount account = user.kakaoAccount();
			return new SocialProfile(AuthProvider.KAKAO, String.valueOf(user.id()), verifiedEmail(account),
					nickname(account));
		}
		catch (RestClientException e) {
			// 코드 만료·재사용, redirect_uri 불일치, 카카오 장애, 시간 초과 등. 키와 토큰은 로그에 남기지 않는다
			log.warn("카카오 로그인에 실패했습니다: {}", e.getClass().getSimpleName());
			throw new SocialLoginFailedException("카카오 로그인에 실패했습니다. 다시 시도해 주세요.");
		}
	}

	private String exchangeToken(String code, String redirectUri) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", properties.clientId());
		form.add("redirect_uri", redirectUri);
		form.add("code", code);
		if (properties.hasClientSecret()) {
			form.add("client_secret", properties.clientSecret());
		}

		KakaoTokenResponse response = restClient.post()
			.uri(properties.tokenUri())
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(KakaoTokenResponse.class);
		if (response == null || response.accessToken() == null) {
			throw new SocialLoginFailedException("카카오 로그인에 실패했습니다. 다시 시도해 주세요.");
		}
		return response.accessToken();
	}

	private KakaoUser loadUser(String accessToken) {
		return restClient.get()
			.uri(properties.userInfoUri())
			.headers(headers -> headers.setBearerAuth(accessToken))
			.retrieve()
			.body(KakaoUser.class);
	}

	// 카카오가 유효하고(is_email_valid) 인증된(is_email_verified) 이메일만 쓴다. 하나라도 아니면 이메일이 없는 것으로 본다
	private static String verifiedEmail(KakaoAccount account) {
		if (account == null) {
			return null;
		}
		boolean verified = Boolean.TRUE.equals(account.isEmailValid()) && Boolean.TRUE.equals(account.isEmailVerified());
		return verified ? account.email() : null;
	}

	private static String nickname(KakaoAccount account) {
		if (account == null || account.profile() == null) {
			return null;
		}
		return account.profile().nickname();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KakaoUser(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KakaoAccount(
			String email,
			@JsonProperty("is_email_valid") Boolean isEmailValid,
			@JsonProperty("is_email_verified") Boolean isEmailVerified,
			KakaoProfile profile) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KakaoProfile(String nickname) {
	}

}