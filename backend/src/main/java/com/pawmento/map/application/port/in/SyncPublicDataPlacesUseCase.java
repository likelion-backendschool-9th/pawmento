package com.pawmento.map.application.port.in;

public interface SyncPublicDataPlacesUseCase {

	/**
	 * 공공데이터 전체를 받아 한 종류의 장소를 통째로 맞춘다. 신뢰할 수 있는 전체 스냅샷일 때만 반영하고, 아니면 아무것도 바꾸지 않고
	 * 실패한다.
	 */
	SyncPublicDataPlacesResult sync(SyncPublicDataPlacesCommand command);

}
