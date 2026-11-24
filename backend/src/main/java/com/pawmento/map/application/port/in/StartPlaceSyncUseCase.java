package com.pawmento.map.application.port.in;

public interface StartPlaceSyncUseCase {

	/**
	 * 한 종류의 가져오기를 시작하고 바로 돌아온다. 가져오는 데 몇 분이 걸려서(병원 약 4분, 미용 약 7분) 요청 안에서 끝까지 기다리면
	 * 배포의 nginx 시간 제한(기본 60초)에 걸려 결과를 받을 수 없기 때문이다. 실제 가져오기는 별도 스레드에서 돌고, 진행·결과는
	 * {@link GetPlaceSyncStatusUseCase}로 조회한다(#172-S22).
	 *
	 * @throws com.pawmento.map.application.exception.PlaceSyncAdminDeniedException 현재 관리자가 아닐 때
	 * @throws com.pawmento.map.application.exception.PlaceSyncAlreadyRunningException 같은 종류를 이미 가져오는 중일 때
	 */
	PlaceSyncStatusResult start(StartPlaceSyncCommand command);

}
