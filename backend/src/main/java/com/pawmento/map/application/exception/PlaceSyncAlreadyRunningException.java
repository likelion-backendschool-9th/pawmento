package com.pawmento.map.application.exception;

import com.pawmento.global.exception.StateConflictException;

// 같은 종류를 이미 가져오는 중이다. 지금 상태 때문에 할 수 없으므로 409다(#172-S22)
public class PlaceSyncAlreadyRunningException extends StateConflictException {

	public PlaceSyncAlreadyRunningException() {
		super("같은 종류의 장소를 이미 가져오는 중입니다. 끝난 뒤 다시 시도해 주세요.");
	}

}
