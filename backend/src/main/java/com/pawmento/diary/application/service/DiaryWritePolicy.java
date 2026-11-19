package com.pawmento.diary.application.service;

import com.pawmento.diary.application.exception.DiaryFeatureUnavailableException;
import com.pawmento.diary.application.port.in.DiaryWriteFeatures;

final class DiaryWritePolicy {
  private DiaryWritePolicy() {}

  static void requireSupported(DiaryWriteFeatures features) {
    if (features.locationRequested()) throw new DiaryFeatureUnavailableException();
  }
}
