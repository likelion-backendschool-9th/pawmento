package com.pawmento.media.adapter.in.web;

import com.pawmento.global.error.*;
import com.pawmento.media.application.port.in.MediaUploadException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
class MediaUploadExceptionHandler {
  @ExceptionHandler(MediaUploadException.class)
  ResponseEntity<ErrorResponse> handle(MediaUploadException e) {
    HttpStatus status =
        switch (e.kind()) {
          case INVALID -> HttpStatus.BAD_REQUEST;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case FORBIDDEN -> HttpStatus.FORBIDDEN;
          case CONFLICT -> HttpStatus.CONFLICT;
          case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
          case UNSUPPORTED -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
          case STORAGE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    ErrorCode code =
        switch (e.kind()) {
          case NOT_FOUND -> ErrorCode.NOT_FOUND;
          case FORBIDDEN -> ErrorCode.FORBIDDEN;
          case CONFLICT -> ErrorCode.CONFLICT;
          case STORAGE -> ErrorCode.STORAGE_UNAVAILABLE;
          default -> ErrorCode.VALIDATION;
        };
    return ResponseEntity.status(status).body(new ErrorResponse(code, e.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(413)
        .body(new ErrorResponse(ErrorCode.VALIDATION, "파일은 15MiB, 요청은 16MiB까지 보낼 수 있어요."));
  }

  @ExceptionHandler(MultipartException.class)
  ResponseEntity<ErrorResponse> invalid(MultipartException e) {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse(ErrorCode.VALIDATION, "파일 전송 형식을 확인해 주세요."));
  }
}
