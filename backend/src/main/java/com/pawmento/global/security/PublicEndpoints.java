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

	// 가입·로그인·소셜 로그인·로그아웃. 이 경로에서는 쿠키의 토큰을 읽지 않는다 (CookieBearerTokenResolver).
	// Security는 컨트롤러 접두어(WebConfig)가 붙기 전의 실제 경로를 보므로 ApiPaths.V1을 붙여 전체 경로로 쓴다
	static final RequestMatcher AUTH = new OrRequestMatcher(
			PATH.matcher(HttpMethod.POST, ApiPaths.V1 + "/auth/signup"),
			PATH.matcher(HttpMethod.POST, ApiPaths.V1 + "/auth/login"),
			PATH.matcher(HttpMethod.POST, ApiPaths.V1 + "/auth/oauth/{provider}"),
			PATH.matcher(HttpMethod.POST, ApiPaths.V1 + "/auth/logout"),
			PATH.matcher(HttpMethod.POST, ApiPaths.V1 + "/auth/email-verifications"));

	// 누구나 보는 읽기 경로. 이 경로에서도 쿠키의 토큰을 읽지 않는다 (CookieBearerTokenResolver).
	// 만료된 쿠키가 남은 사람도 공개 목록을 볼 수 있어야 하기 때문이다.
	// 커뮤니티 글 목록, 카테고리 목록, 글 상세. 와일드카드를 쓰지 않고 지금 있는 경로만 적는다.
	// /posts/* 로 열면 나중에 더할 로그인 전용 경로(예: /posts/me)까지 조용히 공개되기 때문이다
	static final RequestMatcher PUBLIC_READ = new OrRequestMatcher(
			PATH.matcher(HttpMethod.GET, ApiPaths.V1 + "/posts"),
			PATH.matcher(HttpMethod.GET, ApiPaths.V1 + "/posts/categories"),
			PATH.matcher(HttpMethod.GET, ApiPaths.V1 + "/posts/{postId:\\d+}"));

	// API 문서 (Springdoc 기본 경로)
	static final RequestMatcher API_DOCS = new OrRequestMatcher(
			PATH.matcher(HttpMethod.GET, "/v3/api-docs/**"),
			PATH.matcher(HttpMethod.GET, "/swagger-ui/**"),
			PATH.matcher(HttpMethod.GET, "/swagger-ui.html"));

	private PublicEndpoints() {
	}

}
