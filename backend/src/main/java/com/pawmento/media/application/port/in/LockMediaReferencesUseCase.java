package com.pawmento.media.application.port.in;


// 부모의 첨부 FK를 쓰기 전에 관련 자산을 ID 순서로 잠근다.
public interface LockMediaReferencesUseCase {
  void lock(LockMediaReferencesCommand command);
}
