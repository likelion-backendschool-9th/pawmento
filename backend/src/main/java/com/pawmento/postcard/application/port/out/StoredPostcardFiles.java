package com.pawmento.postcard.application.port.out;

import com.pawmento.postcard.domain.PostcardStatus;

// 파일 정리가 보는 엽서 값만 담는다. 도메인으로 복원하지 않아 깨진 행이 있어도 정리가 멈추지 않는다.
public record StoredPostcardFiles(
    Long postcardId, PostcardStatus status, String imageKey, String sourcePhotoKey) {}
