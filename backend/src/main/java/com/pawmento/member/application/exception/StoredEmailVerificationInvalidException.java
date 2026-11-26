package com.pawmento.member.application.exception;

import com.pawmento.global.exception.StoredDataInvalidException;

// 저장된 인증 줄이 도메인 규칙에 맞지 않는다. 요청 오류가 아니라 서버 데이터 문제라 500으로 처리된다
public class StoredEmailVerificationInvalidException extends StoredDataInvalidException {

	public StoredEmailVerificationInvalidException(Throwable cause) {
		super("저장된 이메일 인증이 규칙에 맞지 않습니다.", cause);
	}

}