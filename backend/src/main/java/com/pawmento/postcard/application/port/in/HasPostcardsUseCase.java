package com.pawmento.postcard.application.port.in;

// 반려동물 삭제 전에 pet 도메인이 부른다. 상태와 상관없이 엽서가 하나라도 있으면 true.
public interface HasPostcardsUseCase {
  boolean exists(HasPostcardsQuery query);
}
