package com.pawmento.diary.application.port.in;

// 이번 텍스트 단계에서는 미지원 기능의 요청 여부만 전달하며 원본 JSON은 넘기지 않는다.
public record DiaryWriteFeatures(boolean attachmentsRequested, boolean locationRequested) {
  public static DiaryWriteFeatures textOnly() {
    return new DiaryWriteFeatures(false, false);
  }
}
