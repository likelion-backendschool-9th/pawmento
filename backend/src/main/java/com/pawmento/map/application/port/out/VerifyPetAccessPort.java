package com.pawmento.map.application.port.out;

public interface VerifyPetAccessPort {

	/**
	 * 요청한 회원이 그 반려동물의 보호자인지 확인한다. 반려동물이 없거나 다른 회원의 것이면 예외를 던진다.
	 * 반려동물의 존재와 소유는 pet 도메인이 판단하고, 지도는 그 결과만 받는다.
	 */
	void requireAccess(Long memberId, Long petId);

}
