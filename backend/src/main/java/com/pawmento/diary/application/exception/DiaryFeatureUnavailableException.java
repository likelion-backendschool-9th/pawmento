package com.pawmento.diary.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class DiaryFeatureUnavailableException extends StateConflictException {
  public DiaryFeatureUnavailableException() {
    super("첨부와 위치 연결은 아직 지원하지 않아요.");
  }
}
