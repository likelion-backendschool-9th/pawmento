package com.pawmento.global.security;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

// 액세스 토큰을 담는 쿠키. 발급(로그인)과 삭제(로그아웃)가 같은 속성을 쓰게 한다
@Component
public class AccessTokenCookie {

	public static final String NAME = "ACCESS_TOKEN";

	private final AuthProperties properties;

	AccessTokenCookie(AuthProperties properties) {
		this.properties = properties;
	}

	public ResponseCookie issue(String token, HttpServletRequest request) {
		return cookie(token, properties.accessTokenTtl(), request);
	}

	// 같은 이름·경로의 쿠키를 유효 시간 0으로 내려 브라우저에서 지운다
	public ResponseCookie expire(HttpServletRequest request) {
		return cookie("", Duration.ZERO, request);
	}

	private ResponseCookie cookie(String value, Duration maxAge, HttpServletRequest request) {
		return ResponseCookie.from(NAME, value)
			// 자바스크립트가 토큰을 읽지 못하게 한다 (XSS로 토큰이 새는 것을 막는다)
			.httpOnly(true)
			// 다른 사이트가 보낸 POST 등에는 쿠키가 붙지 않는다 (CSRF 방어). 링크를 눌러 들어오는 이동에는 붙는다
			.sameSite("Lax")
			// HTTPS 요청이면 HTTPS에서만 보내게 한다. 프록시 뒤에서는 forwarded 헤더 설정이 있어야 HTTPS로 인식한다
			.secure(request.isSecure())
			.path("/")
			.maxAge(maxAge)
			.build();
	}

}
