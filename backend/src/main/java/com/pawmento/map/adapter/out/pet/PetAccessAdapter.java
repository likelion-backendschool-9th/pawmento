package com.pawmento.map.adapter.out.pet;

import org.springframework.stereotype.Component;

import com.pawmento.map.application.port.out.VerifyPetAccessPort;
import com.pawmento.pet.application.port.in.GetPetQuery;
import com.pawmento.pet.application.port.in.GetPetUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 반려동물의 존재와 소유 확인을 pet 도메인에 맡긴다. pet의 입력 포트만 부르고 pet의 도메인·저장소는 쓰지 않는다.
 * 반려동물이 없거나 다른 회원의 것이면 pet이 던지는 예외가 그대로 올라간다(404, 403).
 */
@Component
@RequiredArgsConstructor
class PetAccessAdapter implements VerifyPetAccessPort {

	private final GetPetUseCase getPetUseCase;

	@Override
	public void requireAccess(Long memberId, Long petId) {
		getPetUseCase.get(new GetPetQuery(memberId, petId));
	}

}
