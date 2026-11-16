package com.pawmento.global.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

// @LoginMember가 붙은 파라미터에 토큰의 회원 id(sub)를 넣는다
class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(LoginMember.class);
	}

	@Override
	public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		if (!Long.class.equals(parameter.getParameterType())) {
			throw new IllegalStateException("@LoginMember는 Long 타입 파라미터에만 붙일 수 있습니다: " + parameter);
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken token)) {
			// 공개 경로에서 @LoginMember를 쓴 경우. 인증 실패로 처리되어 401이 나간다
			throw new AuthenticationCredentialsNotFoundException("로그인한 회원이 없습니다.");
		}
		return Long.valueOf(token.getToken().getSubject());
	}

}
