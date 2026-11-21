package com.pawmento.postcard.application.port.out;

import com.pawmento.postcard.domain.PostcardSourceKind;

public interface LoadPostcardSourcePort {
  // 원본이 그 반려동물의 것이 아니면 404 예외를 던진다.
  PostcardSource load(Long memberId, Long petId, PostcardSourceKind kind, Long sourceId);

  // 원본에 붙은 사진의 썸네일(JPEG)
  byte[] photo(Long memberId, Long petId, PostcardSourceKind kind, Long sourceId, Long assetId);
}
