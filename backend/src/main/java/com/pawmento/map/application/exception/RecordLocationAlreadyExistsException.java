package com.pawmento.map.application.exception;

// 같은 기록에 위치를 처음 저장하는 요청이 동시에 들어와, 먼저 저장된 위치와 겹칠 때
public class RecordLocationAlreadyExistsException extends RuntimeException {

	public RecordLocationAlreadyExistsException(Long recordId, Throwable cause) {
		super("이미 위치가 있는 기록입니다. recordId=" + recordId, cause);
	}

}
