package com.pawmento.global.error;

import com.pawmento.global.exception.*;
import java.lang.reflect.RecordComponent;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.*;

// 공통 기본 예외와 요청 오류를 응답으로 바꾼다. 개별 도메인 예외는 import하지 않는다.
@RestControllerAdvice
@Slf4j
class GlobalExceptionHandler {

  @ExceptionHandler(RuleViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ErrorResponse handle(RuleViolationException e) {
    return new ErrorResponse(ErrorCode.VALIDATION, e.getMessage());
  }

  @ExceptionHandler(PermissionDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  ErrorResponse handle(PermissionDeniedException e) {
    return new ErrorResponse(ErrorCode.FORBIDDEN, e.getMessage());
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  ErrorResponse handle(ResourceNotFoundException e) {
    return new ErrorResponse(ErrorCode.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(StateConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  ErrorResponse handle(StateConflictException e) {
    return new ErrorResponse(ErrorCode.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(ContentLimitExceededException.class)
  @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
  ErrorResponse handle(ContentLimitExceededException e) {
    return new ErrorResponse(ErrorCode.VALIDATION, e.getMessage());
  }

  @ExceptionHandler(UnsupportedFormatException.class)
  @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
  ErrorResponse handle(UnsupportedFormatException e) {
    return new ErrorResponse(ErrorCode.VALIDATION, e.getMessage());
  }

  @ExceptionHandler(StorageUnavailableException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  ErrorResponse handle(StorageUnavailableException e) {
    log.error("Storage dependency unavailable", e);
    return new ErrorResponse(ErrorCode.STORAGE_UNAVAILABLE, e.getMessage());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
        .body(new ErrorResponse(ErrorCode.VALIDATION, "파일은 15MiB, 요청은 16MiB까지 보낼 수 있어요."));
  }

  @ExceptionHandler(MultipartException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ErrorResponse invalidMultipart(MultipartException e) {
    return new ErrorResponse(ErrorCode.VALIDATION, "파일 전송 형식을 확인해 주세요.");
  }

  // @Valid 검증 실패. 오류가 여러 개면 요청 본문에 선언된 순서로 가장 앞 필드의 문구를 내린다.
  // 검증기가 주는 순서는 실행할 때마다 달라서, 같은 요청에 같은 응답이 나가도록 순서를 정한다
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ErrorResponse handle(MethodArgumentNotValidException e) {
    BindingResult result = e.getBindingResult();
    List<String> declaredFields = declaredFields(result.getTarget());
    String message =
        result.getFieldErrors().stream()
            .min(
                Comparator.comparingInt(
                        (FieldError error) -> declaredOrder(declaredFields, error.getField()))
                    .thenComparing(FieldError::getField)
                    .thenComparing(error -> String.valueOf(error.getDefaultMessage())))
            .map(GlobalExceptionHandler::message)
            .orElse("입력값을 확인해 주세요.");
    return new ErrorResponse(ErrorCode.VALIDATION, message);
  }

  // 본문이 없거나 JSON 형식이 아닌 요청
  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ErrorResponse handle(HttpMessageNotReadableException e) {
    return new ErrorResponse(ErrorCode.VALIDATION, "요청 형식이 올바르지 않습니다.");
  }

  private static List<String> declaredFields(Object target) {
    if (target == null || !target.getClass().isRecord()) {
      return List.of();
    }
    return List.of(target.getClass().getRecordComponents()).stream()
        .map(RecordComponent::getName)
        .toList();
  }

  // 요청 본문의 필드가 아닌 이름으로 온 오류는 맨 뒤로 보낸다.
  // 순서가 필요한 검증은 해당 필드에 붙인다(예: 비밀번호 바이트 제한은 password 필드의 @MaxUtf8Bytes)
  private static int declaredOrder(List<String> declaredFields, String field) {
    int index = declaredFields.indexOf(field);
    return index >= 0 ? index : Integer.MAX_VALUE;
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ErrorResponse handle(MethodArgumentTypeMismatchException e) {
    return new ErrorResponse(ErrorCode.VALIDATION, invalidFormatMessage(e.getName()));
  }

  private static String message(FieldError error) {
    if ("typeMismatch".equals(error.getCode())) {
      return invalidFormatMessage(error.getField());
    }
    return error.getDefaultMessage();
  }

  private static String invalidFormatMessage(String name) {
    return name + " 값의 형식이 올바르지 않습니다.";
  }
}
