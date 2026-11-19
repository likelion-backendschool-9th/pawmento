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
class GoogleProfileClient implements SocialProfileClient {
    private static final Logger log = LoggerFactory.getLogger(GoogleProfileClient.class);

    private final GoogleOAuthProperties properties;

    private final RestClient restClient;

    @Autowired 
    GoogleProfileClient(GoogleOAuthProperties properties) {
        this(properties, builderWithTimeouts(properties));
    }

    GoogleProfileClient(GoogleOAuthProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    private static RestClient.Builder builderWithTimeouts(GoogleOAuthProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override 
    public SocialProfile load(String code, String state, String redirectUri) {
        if (!properties.hasCredentials()) {
            log.warn("구글 OAuth 키가 설정되지 않아 구글 로그인을 할 수 없습니다.");
            throw new SocialLoginFailedException("지금은 구글 로그인을 사용할 수 없습니다.");
        }
        try {
			String accessToken = exchangeToken(code, redirectUri);
			GoogleUserInfo user = loadUserInfo(accessToken);
			if (user == null || user.sub() == null) {
				throw new SocialLoginFailedException("구글 사용자 정보를 받지 못했습니다.");
			}
			// 구글이 확인한 이메일만 쓴다. 확인되지 않았으면 이메일이 없는 것으로 본다
			String email = Boolean.TRUE.equals(user.emailVerified()) ? user.email() : null;
			return new SocialProfile(AuthProvider.GOOGLE, user.sub(), email, user.name());
		}
		catch (RestClientException e) {
			// 코드 만료·재사용, redirect_uri 불일치, 구글 장애, 시간 초과 등. 키와 토큰은 로그에 남기지 않는다
			log.warn("구글 로그인에 실패했습니다: {}", e.getClass().getSimpleName());
			throw new SocialLoginFailedException("구글 로그인에 실패했습니다. 다시 시도해 주세요.");
		}
    }

    private String exchangeToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", redirectUri);

        GoogleTokenResponse response = restClient.post()
            .uri(properties.tokenUri())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(GoogleTokenResponse.class);
        if (response == null || response.accessToken() == null) {
            throw new SocialLoginFailedException("구글 로그인에 실패했습니다. 다시 시도해 주세요.");
        }
        return response.accessToken();
    }

    private GoogleUserInfo loadUserInfo(String accessToken) {
        return restClient.get()
            .uri(properties.userInfoUri())
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .body(GoogleUserInfo.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleUserInfo(
        String sub,
        String email,
        @JsonProperty("email_verified") Boolean emailVerified,
        String name) {}
}
