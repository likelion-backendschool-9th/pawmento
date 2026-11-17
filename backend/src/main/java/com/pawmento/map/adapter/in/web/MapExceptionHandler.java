package com.pawmento.map.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.pawmento.global.error.ErrorCode;
import com.pawmento.global.error.ErrorResponse;
import com.pawmento.map.application.exception.InvalidMapAreaException;

// 지도 도메인의 예외를 HTTP 응답으로 바꾼다
@RestControllerAdvice
class MapExceptionHandler {

	@ExceptionHandler(InvalidMapAreaException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ErrorResponse handle(InvalidMapAreaException e) {
		return new ErrorResponse(ErrorCode.VALIDATION, e.getMessage());
	}

}
