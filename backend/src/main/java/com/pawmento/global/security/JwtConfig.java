package com.pawmento.global.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

// JWT 발급(JwtEncoder)과 검증(JwtDecoder)이 같은 키와 알고리즘(HS256)을 쓰게 한다
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
class JwtConfig {

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	// HS256은 256비트(32바이트) 이상의 키가 필요하다
	private static final int MIN_SECRET_BYTES = 32;

	@Bean
	JwtSigningKey jwtSigningKey(AuthProperties properties) {
		return new JwtSigningKey(new SecretKeySpec(secretBytes(properties.jwtSecret()), "HmacSHA256"));
	}

	@Bean
	JwtEncoder jwtEncoder(JwtSigningKey signingKey) {
		return NimbusJwtEncoder.withSecretKey(signingKey.value()).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder(JwtSigningKey signingKey) {
		return NimbusJwtDecoder.withSecretKey(signingKey.value()).macAlgorithm(MacAlgorithm.HS256).build();
	}

	private static byte[] secretBytes(String secret) {
		if (secret == null || secret.isBlank()) {
			// 키를 저장소에 두지 않으면서 로컬에서 설정 없이 실행되게 한다. 고정된 기본 키를 두면 배포 환경에서 그 키가 그대로 쓰일 수 있다
			log.warn("JWT_SECRET이 없어 임시 서명 키를 만들었습니다. 재시작하면 기존 로그인이 모두 풀립니다. "
					+ "개발 서버와 운영 서버에서는 JWT_SECRET을 설정하세요.");
			byte[] random = new byte[MIN_SECRET_BYTES];
			new SecureRandom().nextBytes(random);
			return random;
		}
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"JWT_SECRET은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다. 현재 " + bytes.length + "바이트입니다.");
		}
		return bytes;
	}

	record JwtSigningKey(SecretKey value) {
	}

}
