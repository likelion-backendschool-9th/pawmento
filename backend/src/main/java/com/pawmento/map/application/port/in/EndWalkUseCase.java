package com.pawmento.map.application.port.in;

public interface EndWalkUseCase {

	/**
	 * 산책을 끝낸다. 이미 끝났거나 중단된 산책은 바꾸지 않고 지금 상태를 돌려준다.
	 * 산책이 없으면 404, 다른 회원의 산책이면 403이다.
	 */
	EndWalkResult end(EndWalkCommand command);

}
