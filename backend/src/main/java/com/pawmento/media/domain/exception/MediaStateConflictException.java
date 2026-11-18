package com.pawmento.media.domain.exception;

import com.pawmento.global.exception.StateConflictException;

public class MediaStateConflictException extends StateConflictException {
  public enum Reason {
    ALREADY_RECEIVED("이미 파일을 받았거나 처리 중인 업로드예요."),
    SESSION_EXPIRED("업로드 시간이 지났어요. 새로 파일을 선택해 주세요."),
    NOT_UPLOADING("파일을 받을 수 없는 상태예요."),
    INVALID_CLEANUP_STATE("파일 정리 상태를 확인해 주세요."),
    NOT_PROCESSING("검증 중인 파일이 아니에요."),
    TYPE_CHANGED("검증한 파일의 종류가 달라요."),
    PREVIEW_UNAVAILABLE("미리보기를 할 수 없는 파일이에요."),
    FILE_REFERENCED("글에 연결된 파일은 삭제할 수 없어요."),
    NOT_DELETING("삭제 중인 파일이 아니에요.");
    private final String message;

    Reason(String message) {
      this.message = message;
    }
  }

  public MediaStateConflictException(Reason reason) {
    super(reason.message);
  }
}
