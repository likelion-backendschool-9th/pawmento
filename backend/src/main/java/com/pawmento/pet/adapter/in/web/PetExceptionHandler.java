package com.pawmento.pet.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.pawmento.global.error.ErrorCode;
import com.pawmento.global.error.ErrorResponse;
import com.pawmento.pet.application.exception.InvalidPetException;
import com.pawmento.pet.application.exception.PetAccessDeniedException;
import com.pawmento.pet.application.exception.PetHasRecordsException;
import com.pawmento.pet.application.exception.PetNotFoundException;

@RestControllerAdvice
class PetExceptionHandler {

	@ExceptionHandler(InvalidPetException.class)
	ResponseEntity<ErrorResponse> handle(InvalidPetException e) {
		return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, e);
	}

	@ExceptionHandler(PetAccessDeniedException.class)
	ResponseEntity<ErrorResponse> handle(PetAccessDeniedException e) {
		return response(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, e);
	}

	@ExceptionHandler(PetNotFoundException.class)
	ResponseEntity<ErrorResponse> handle(PetNotFoundException e) {
		return response(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, e);
	}

	@ExceptionHandler(PetHasRecordsException.class)
	ResponseEntity<ErrorResponse> handle(PetHasRecordsException e) {
		return response(HttpStatus.CONFLICT, ErrorCode.CONFLICT, e);
	}

	private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorCode code, RuntimeException e) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, e.getMessage()));
	}

}
