package com.pawmento.global.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.pawmento.global.web.ApiPaths;

// 로그인 없이 부를 수 있는 경로. 여기에 없는 경로는 모두 로그인이 필요하다.
// 바꾸면 docs/member/README.md의 공개 경로 표도 함께 고친다
final class PublicEndpoints {

	private static final PathPatternRequestMatcher.Builder PATH = PathPatternRequestMatcher.withDefaults();

	// 가입·로그인·로그아웃. 이 경로에서는 쿠키의 토큰을 읽지 않는다 (CookieBearerTokenResolver).
	// Security는 컨트롤러 접두어(WebConfig)가 붙기 전의 실제 경로를 보므로 ApiPaths.V1을 붙여 전체 경로로 쓴다
	static final RequestMatcher AUTH = new OrRequestMatcher(
			PATH.matcher(HttpMethod.POST, ApiPaths.V1 + "/auth/signup"),
			PATH.matcher(HttpMethod.POST, ApiPaths.V1 + "/auth/login"),
			PATH.matcher(HttpMethod.POST, ApiPaths.V1 + "/auth/logout"));

	// API 문서 (Springdoc 기본 경로)
	static final RequestMatcher API_DOCS = new OrRequestMatcher(
			PATH.matcher(HttpMethod.GET, "/v3/api-docs/**"),
			PATH.matcher(HttpMethod.GET, "/swagger-ui/**"),
			PATH.matcher(HttpMethod.GET, "/swagger-ui.html"));

	private PublicEndpoints() {
	}

}
