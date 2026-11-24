package com.pawmento.map.application.exception;

import com.pawmento.global.exception.StorageUnavailableException;

// 가져오는 동안 DB 연결이 끊기는 등으로 종류별 잠금을 잃어서, 저장하지 않고 중단했다. 다른 실행이 같은 종류를 이미 시작했을 수 있어
// 오래된 스냅샷으로 덮어쓰지 않으려는 것이다. 다시 시도하면 성공할 수 있어 일시 장애(503)로 응답한다(#172-S22)
public class PlaceSyncLockLostException extends StorageUnavailableException {

	public PlaceSyncLockLostException(Throwable cause) {
		super("가져오는 동안 잠금을 잃어 저장하지 않았습니다. 저장된 장소는 바뀌지 않았습니다. 잠시 뒤 다시 시도해 주세요.", cause);
	}

}
