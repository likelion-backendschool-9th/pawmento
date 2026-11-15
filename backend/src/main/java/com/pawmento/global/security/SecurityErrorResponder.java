package com.pawmento.global.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.pawmento.global.error.ErrorCode;
import com.pawmento.global.error.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

// 인증·권한 실패를 컨트롤러의 에러 응답과 같은 JSON 형식으로 내린다.
// 필터 단계에서 일어나는 실패라 @RestControllerAdvice가 받지 못한다
@Component
class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	SecurityErrorResponder(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	// 로그인하지 않았거나 토큰이 만료·위조된 경우
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		write(response, HttpStatus.UNAUTHORIZED, new ErrorResponse(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다."));
	}

	// 로그인은 했지만 권한이 없는 경우
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(response, HttpStatus.FORBIDDEN, new ErrorResponse(ErrorCode.FORBIDDEN, "권한이 없습니다."));
	}

	private void write(HttpServletResponse response, HttpStatus status, ErrorResponse body) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(jsonMapper.writeValueAsString(body));
	}

}
