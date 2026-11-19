package com.pawmento.member.adapter.out.oauth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("pawmento.oauth.kakao")
public record KakaoOAuthProperties(
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,
        @DefaultValue("https://Oauth.kakao.com/oauth/token") String tokenUri,
        @DefaultValue("https://kapi.kakao.com/v2/user/me") String userInfoUri,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout) {

    boolean hasCredentials() {
        return !clientId.isBlank();
    }

    boolean hasClientSecret() {
        return !clientSecret.isBlank();
    }
    
}
