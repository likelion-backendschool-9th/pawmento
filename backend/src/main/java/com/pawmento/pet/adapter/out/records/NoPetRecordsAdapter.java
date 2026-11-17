package com.pawmento.pet.adapter.out.records;

import org.springframework.stereotype.Component;

import com.pawmento.pet.application.port.out.HasPetRecordsPort;

// 실제 기록(diary 등) 어댑터가 붙기 전 기본값. 기록이 없다고 본다
@Component
public class NoPetRecordsAdapter implements HasPetRecordsPort {

	@Override
	public boolean existsByPetId(Long petId) {
		return false;
	}

}
