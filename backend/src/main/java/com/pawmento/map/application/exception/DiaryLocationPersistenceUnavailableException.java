package com.pawmento.map.application.exception;

import com.pawmento.global.exception.StorageUnavailableException;

// 기록 위치 저장소(DB)를 잠시 쓸 수 없다. 기술 예외를 서비스까지 올리지 않고 이 예외로 바꿔 503으로 응답한다
public class DiaryLocationPersistenceUnavailableException extends StorageUnavailableException {

	public DiaryLocationPersistenceUnavailableException(Throwable cause) {
		super("위치 정보를 잠시 처리할 수 없습니다. 잠시 뒤 다시 시도해 주세요.", cause);
	}

}
