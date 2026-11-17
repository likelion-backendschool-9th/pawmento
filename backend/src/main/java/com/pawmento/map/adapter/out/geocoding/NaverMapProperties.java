package com.pawmento.map.adapter.out.geocoding;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 네이버 지도 서버 호출 설정 (application.yml의 pawmento.map.naver).
 *
 * @param clientId 애플리케이션 Client ID. 환경 변수 NAVER_MAP_CLIENT_ID로 받는다
 * @param clientSecret 애플리케이션 Client Secret. 환경 변수 NAVER_MAP_CLIENT_SECRET으로만 받는다. 저장소에 두지 않는다
 * @param baseUrl 호출 주소
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout 응답 제한 시간
 */
@ConfigurationProperties("pawmento.map.naver")
public record NaverMapProperties(
		@DefaultValue("") String clientId,
		@DefaultValue("") String clientSecret,
		@DefaultValue("https://maps.apigw.ntruss.com") String baseUrl,
		@DefaultValue("1s") Duration connectTimeout,
		@DefaultValue("2s") Duration readTimeout) {

	boolean hasCredentials() {
		return !clientId.isBlank() && !clientSecret.isBlank();
	}

}
