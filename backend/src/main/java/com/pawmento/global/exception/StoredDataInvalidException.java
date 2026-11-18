package com.pawmento.global.exception;

// 이미 저장된 데이터가 도메인 규칙에 맞지 않아 읽을 수 없다. 요청자의 입력 오류가 아니라 서버 쪽 데이터 문제다
public abstract class StoredDataInvalidException extends RuntimeException {
  protected StoredDataInvalidException(String message, Throwable cause) {
    super(message, cause);
  }
}
