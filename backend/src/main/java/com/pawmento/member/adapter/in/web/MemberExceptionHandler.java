package com.pawmento.member.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.pawmento.global.error.ErrorCode;
import com.pawmento.global.error.ErrorResponse;
import com.pawmento.member.application.exception.InvalidCredentialsException;
import com.pawmento.member.application.exception.SocialLoginFailedException;

// 로그인 실패(401)는 공통 기본 예외로 다룰지 아직 정하지 않아 여기서 응답으로 바꾼다 (docs/backend-exceptions.md)
@RestControllerAdvice
class MemberExceptionHandler {

	@ExceptionHandler(SocialLoginFailedException.class)
	ResponseEntity<ErrorResponse> handle(SocialLoginFailedException e) {
		return response(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, e);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ErrorResponse> handle(InvalidCredentialsException e) {
		return response(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, e);
	}

	private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorCode code, RuntimeException e) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, e.getMessage()));
	}

}
