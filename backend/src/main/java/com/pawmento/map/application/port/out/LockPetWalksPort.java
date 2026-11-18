package com.pawmento.map.application.port.out;

public interface LockPetWalksPort {

	/**
	 * 반려동물의 산책을 시작하거나 바꾸는 일이 동시에 하나씩만 진행되게 잠근다.
	 * 반려동물당 진행 중인 산책을 하나로 지키려는 용도이고, 잠금은 호출한 트랜잭션이 끝날 때까지 유지된다.
	 */
	void lock(Long petId);

}
