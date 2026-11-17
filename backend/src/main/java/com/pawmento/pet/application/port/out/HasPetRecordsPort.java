package com.pawmento.pet.application.port.out;

// 일·질문 등 실제 기록 도메인이 어댑터로 연결한다. 샘플 com.pawmento.record 는 쓰지 않는다
public interface HasPetRecordsPort {

	boolean existsByPetId(Long petId);

}
