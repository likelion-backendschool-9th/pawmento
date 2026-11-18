package com.pawmento.pet.application.port.out;

// diary·question 등 실제 기록 도메인이 어댑터로 연결한다.
public interface HasPetRecordsPort {

	boolean existsByPetId(Long petId);

}
