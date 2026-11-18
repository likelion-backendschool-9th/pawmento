package com.pawmento.member.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.pawmento.global.error.ErrorCode;
import com.pawmento.global.error.ErrorResponse;
import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.exception.InvalidCredentialsException;
import com.pawmento.member.application.exception.InvalidNicknameException;
import com.pawmento.member.application.exception.MemberNotFoundException;
import com.pawmento.member.application.exception.NicknameChangeLimitException;
import com.pawmento.member.application.exception.SocialEmailConflictException;
import com.pawmento.member.application.exception.SocialLoginFailedException;
import com.pawmento.member.application.exception.UnsupportedSocialProviderException;

// 회원 도메인의 예외를 HTTP 응답으로 바꾼다
@RestControllerAdvice
class MemberExceptionHandler {


	// 공통 기본 예외(#60)가 develop에 들어오면 기본 예외 상속으로 옮기고 아래 핸들러는 지운다
	@ExceptionHandler(DuplicateEmailException.class)
	ResponseEntity<ErrorResponse> handle(DuplicateEmailException e) {
		return response(HttpStatus.CONFLICT, ErrorCode.CONFLICT, e);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ErrorResponse> handle(InvalidCredentialsException e) {
		return response(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, e);
	}

	@ExceptionHandler(MemberNotFoundException.class)
	ResponseEntity<ErrorResponse> handle(MemberNotFoundException e) {
		return response(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, e);
	}

	@ExceptionHandler(InvalidNicknameException.class)
	ResponseEntity<ErrorResponse> handle(InvalidNicknameException e) {
		return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, e);
	}

	@ExceptionHandler(NicknameChangeLimitException.class)
	ResponseEntity<ErrorResponse> handle(NicknameChangeLimitException e) {
		return response(HttpStatus.CONFLICT, ErrorCode.CONFLICT, e);
	}

		@ExceptionHandler(SocialEmailConflictException.class)
	ResponseEntity<ErrorResponse> handle(SocialEmailConflictException e) {
		return response(HttpStatus.CONFLICT, ErrorCode.CONFLICT, e);
	}

	@ExceptionHandler(SocialLoginFailedException.class)
	ResponseEntity<ErrorResponse> handle(SocialLoginFailedException e) {
		return response(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, e);
	}

	@ExceptionHandler(UnsupportedSocialProviderException.class)
	ResponseEntity<ErrorResponse> handle(UnsupportedSocialProviderException e) {
		return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, e);
	}

	private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorCode code, RuntimeException e) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, e.getMessage()));
	}

}
