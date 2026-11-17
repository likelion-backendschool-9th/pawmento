package com.pawmento.global.error;

import java.lang.reflect.RecordComponent;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// 모든 도메인에 공통인 요청 오류를 에러 응답 형식으로 바꾼다. 도메인 예외는 각 도메인의 핸들러가 맡는다
@RestControllerAdvice
class GlobalExceptionHandler {

	// @Valid 검증 실패. 오류가 여러 개면 요청 본문에 선언된 순서로 가장 앞 필드의 문구를 내린다.
	// 검증기가 주는 순서는 실행할 때마다 달라서, 같은 요청에 같은 응답이 나가도록 순서를 정한다
	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ErrorResponse handle(MethodArgumentNotValidException e) {
		BindingResult result = e.getBindingResult();
		List<String> declaredFields = declaredFields(result.getTarget());
		String message = result.getFieldErrors()
			.stream()
			.min(Comparator.comparingInt((FieldError error) -> declaredOrder(declaredFields, error.getField()))
				.thenComparing(FieldError::getField)
				.thenComparing(error -> String.valueOf(error.getDefaultMessage())))
			.map(GlobalExceptionHandler::message)
			.orElse("입력값을 확인해 주세요.");
		return new ErrorResponse(ErrorCode.VALIDATION, message);
	}

	// 경로 변수나 쿼리 문자열의 값을 숫자 같은 타입으로 바꾸지 못한 요청 (예: /pets/abc)
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ErrorResponse handle(MethodArgumentTypeMismatchException e) {
		return new ErrorResponse(ErrorCode.VALIDATION, invalidFormatMessage(e.getName()));
	}

	// 본문이 없거나 JSON 형식이 아닌 요청
	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ErrorResponse handle(HttpMessageNotReadableException e) {
		return new ErrorResponse(ErrorCode.VALIDATION, "요청 형식이 올바르지 않습니다.");
	}

	// 값을 타입으로 바꾸지 못했을 때(typeMismatch)의 기본 문구는 스프링 내부 영어 메시지라서 쓰지 않는다
	private static String message(FieldError error) {
		if ("typeMismatch".equals(error.getCode())) {
			return invalidFormatMessage(error.getField());
		}
		return error.getDefaultMessage();
	}

	private static String invalidFormatMessage(String name) {
		return name + " 값의 형식이 올바르지 않습니다.";
	}

	private static List<String> declaredFields(Object target) {
		if (target == null || !target.getClass().isRecord()) {
			return List.of();
		}
		return List.of(target.getClass().getRecordComponents()).stream().map(RecordComponent::getName).toList();
	}

	// 요청 본문의 필드가 아닌 이름으로 온 오류는 맨 뒤로 보낸다.
	// 순서가 필요한 검증은 해당 필드에 붙인다(예: 비밀번호 바이트 제한은 password 필드의 @MaxUtf8Bytes)
	private static int declaredOrder(List<String> declaredFields, String field) {
		int index = declaredFields.indexOf(field);
		return index >= 0 ? index : Integer.MAX_VALUE;
	}

}
