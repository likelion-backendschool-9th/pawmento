package com.pawmento.map.application.port.in;

public interface AddWalkPointsUseCase {

	/**
	 * 진행 중인 산책에 좌표 묶음을 쌓는다. 한 요청은 한 트랜잭션이라 하나라도 거절되면 아무것도 쌓이지 않는다.
	 * 같은 산책에서 기록 시각이 같은 좌표는 다시 쌓지 않는다(재전송). 산책이 없으면 404, 다른 회원의 산책이면 403,
	 * 끝났거나 중단된 산책이면 409, 좌표가 범위를 벗어나거나 묶음이 비었거나 100개를 넘으면 400,
	 * 새로 쌓으면 산책 전체가 5,000개를 넘으면 409다.
	 */
	void add(AddWalkPointsCommand command);

}
