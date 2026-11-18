package com.pawmento.media.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class MediaAttachmentConflictException extends StateConflictException {
  public MediaAttachmentConflictException() {
    super("연결된 파일은 삭제할 수 없어요.");
  }
}
