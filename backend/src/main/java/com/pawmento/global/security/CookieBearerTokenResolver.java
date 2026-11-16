package com.pawmento.global.security;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

// Authorization 헤더 대신 쿠키에서 액세스 토큰을 읽는다
class CookieBearerTokenResolver implements BearerTokenResolver {

	@Override
	public String resolve(HttpServletRequest request) {
		// 만료된 쿠키가 남아 있어도 다시 로그인·가입·로그아웃할 수 있어야 한다.
		// 토큰을 넘기면 검증에 실패해 공개 경로인데도 401이 된다
		if (PublicEndpoints.AUTH.matches(request)) {
			return null;
		}
		Cookie cookie = WebUtils.getCookie(request, AccessTokenCookie.NAME);
		if (cookie == null || cookie.getValue().isBlank()) {
			return null;
		}
		return cookie.getValue();
	}

}
