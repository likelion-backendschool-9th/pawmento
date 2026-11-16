package com.pawmento.global.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 인증 설정 (application.yml의 pawmento.auth).
 *
 * @param jwtSecret JWT 서명 키. 환경 변수 JWT_SECRET으로만 받는다. 비어 있으면 시작할 때 임시 키를 만든다
 * @param accessTokenTtl 액세스 토큰과 쿠키의 유효 시간
 */
@ConfigurationProperties("pawmento.auth")
public record AuthProperties(@DefaultValue("") String jwtSecret, @DefaultValue("1h") Duration accessTokenTtl) {
}
