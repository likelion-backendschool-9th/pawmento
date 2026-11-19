package com.pawmento.member.adapter.out.oauth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;



/**
 * 구글 OAuth 설정 (application.yml의 pawmento.oauth.google).
 *
 * @param clientId Google Cloud Console의 OAuth 클라이언트 ID. 환경 변수 GOOGLE_CLIENT_ID로 받는다
 * @param clientSecret 클라이언트 보안 비밀. 환경 변수 GOOGLE_CLIENT_SECRET으로만 받는다. 저장소에 두지 않는다
 * @param tokenUri 인가 코드를 액세스 토큰으로 바꾸는 주소
 * @param userInfoUri 사용자 정보를 받는 주소
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout 응답 제한 시간
 */

@ConfigurationProperties("pawmento.oauth.google")
public record GoogleOAuthProperties (
            @DefaultValue("") String clientId,
            @DefaultValue("") String clientSecret,
            @DefaultValue("https://oauth2.googleapis.com/token") String tokenUri,
            @DefaultValue("https://openidconnect.googleapis.com/v1/userinfo") String userInfoUri,
            @DefaultValue ("2s") Duration connectTimeout,
            @DefaultValue ("3s") Duration readTimeout) {

        boolean hasCredentials() {
            return !clientId.isBlank() && !clientSecret.isBlank();
        }
    }

