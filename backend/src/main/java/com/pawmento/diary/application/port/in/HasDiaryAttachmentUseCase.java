package com.pawmento.diary.application.port.in;

// media 정리의 잠금 범위에서 실제 관계를 검사하기 위한 내부 입력 포트.
public interface HasDiaryAttachmentUseCase {
  boolean exists(Long assetId);
}
