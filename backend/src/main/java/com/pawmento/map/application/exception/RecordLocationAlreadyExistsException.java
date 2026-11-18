package com.pawmento.map.application.exception;

import com.pawmento.global.exception.StateConflictException;

// 같은 기록에 위치가 이미 있다. 이미 저장된 상태 때문에 할 수 없으므로 409다
public class RecordLocationAlreadyExistsException extends StateConflictException {

	public RecordLocationAlreadyExistsException(Long recordId, Throwable cause) {
		super("이미 위치가 있는 기록입니다. recordId=" + recordId, cause);
	}

}
