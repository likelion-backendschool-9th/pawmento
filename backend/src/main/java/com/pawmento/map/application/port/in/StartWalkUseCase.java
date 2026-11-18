package com.pawmento.map.application.port.in;

public interface StartWalkUseCase {

	/**
	 * 반려동물과 산책을 시작한다. 반려동물이 없으면 404, 다른 회원의 것이면 403이고,
	 * 이미 진행 중인 산책이 있으면 시작하지 않는다.
	 */
	StartWalkResult start(StartWalkCommand command);

}
