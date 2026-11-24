package com.pawmento.map.application.port.in;

public interface GetPlaceSyncStatusUseCase {

	/** 한 종류의 마지막 가져오기 상태를 돌려준다. 이 서버가 켜진 뒤 한 번도 돌리지 않았으면 상태는 NONE이다. */
	PlaceSyncStatusResult get(GetPlaceSyncStatusQuery query);

}
