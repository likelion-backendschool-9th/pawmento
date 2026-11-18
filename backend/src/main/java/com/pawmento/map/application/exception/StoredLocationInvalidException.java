package com.pawmento.map.application.exception;

import com.pawmento.global.exception.StoredDataInvalidException;

// 저장된 지도 데이터(위치, 산책 좌표)가 도메인 규칙에 맞지 않아 복원하지 못했다
public class StoredLocationInvalidException extends StoredDataInvalidException {

	public StoredLocationInvalidException(String message, Throwable cause) {
		super(message, cause);
	}

}
