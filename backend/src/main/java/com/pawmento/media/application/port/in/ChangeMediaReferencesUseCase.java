package com.pawmento.media.application.port.in;

// 부모와 실제 첨부 관계를 변경하는 트랜잭션 안에서 호출한다.
public interface ChangeMediaReferencesUseCase {
  void change(ChangeMediaReferencesCommand command);
}
