package com.pawmento.map.application.port.in;

import java.util.Optional;

public interface GetCurrentWalkUseCase {

	/**
	 * 회원이 반려동물과 하고 있는 산책 중 지금 진행 중인 것을 경로와 함께 돌려준다. 새로고침한 화면이 이어서 걷게 하는 용도다.
	 * 진행 중이란 끝내지 않았고 마지막 활동 시각(좌표를 받은 시각, 좌표를 받기 전에는 시작 시각)으로부터 5분 안인 것이며,
	 * 산책을 시작한 날짜와 상관없다. 없으면 비어 있다.
	 * 만든 회원 본인의 산책만 나오고, 다른 회원의 반려동물을 조회하면 403이 아니라 비어 있다(핀·날짜별 조회와 같다).
	 */
	Optional<GetCurrentWalkResult> get(GetCurrentWalkQuery query);

}
