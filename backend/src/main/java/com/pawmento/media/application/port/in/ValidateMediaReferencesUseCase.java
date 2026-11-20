package com.pawmento.media.application.port.in;

/** 외부 주소 조회 전의 짧은 읽기 검사. 저장 시 재검사와 잠금을 대체하지 않는다. */
public interface ValidateMediaReferencesUseCase {
  void validate(ValidateMediaReferencesCommand command);
}
