package com.pawmento.media.application.port.in;

import java.util.List;

// 부모의 첨부 FK를 쓰기 전에 관련 자산을 ID 순서로 잠근다.
public interface LockMediaReferencesUseCase {
  void lock(List<Long> assetIds);
}
