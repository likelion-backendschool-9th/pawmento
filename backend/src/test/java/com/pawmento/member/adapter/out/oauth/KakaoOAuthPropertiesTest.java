package com.pawmento.member.adapter.out.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

// 클라이언트 테스트는 주소를 직접 넣어 이 기본값과 설정 경로를 거치지 않는다. 여기서 따로 확인한다
class KakaoOAuthPropertiesTest {

	private static final String PREFIX = "pawmento.oauth.kakao";

	@Test
	@DisplayName("#40-S11 설정 경로는 pawmento.oauth.kakao이고, 키만 넣으면 카카오 공식 주소와 기본 시간 제한을 쓴다")
	void defaultsPointToKakao() {
		Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
				PREFIX + ".client-id", "rest-api-key",
				PREFIX + ".client-secret", "kakao-secret")));

		KakaoOAuthProperties properties = binder.bindOrCreate(PREFIX, KakaoOAuthProperties.class);

		assertThat(KakaoOAuthProperties.class.getAnnotation(ConfigurationProperties.class).value()).isEqualTo(PREFIX);
		assertThat(properties.clientId()).isEqualTo("rest-api-key");
		assertThat(properties.clientSecret()).isEqualTo("kakao-secret");
		assertThat(properties.tokenUri()).isEqualTo("https://kauth.kakao.com/oauth/token");
		assertThat(properties.userInfoUri()).isEqualTo("https://kapi.kakao.com/v2/user/me");
		assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
		assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(3));
	}
}