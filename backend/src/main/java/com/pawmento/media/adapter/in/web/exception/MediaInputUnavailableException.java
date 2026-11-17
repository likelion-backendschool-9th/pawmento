package com.pawmento.media.adapter.in.web.exception;

import com.pawmento.global.exception.StorageUnavailableException;

public class MediaInputUnavailableException extends StorageUnavailableException {
  public MediaInputUnavailableException(Throwable cause) {
    super("파일 입력을 읽거나 닫지 못했어요. 잠시 후 다시 시도해 주세요.", cause);
  }
}
