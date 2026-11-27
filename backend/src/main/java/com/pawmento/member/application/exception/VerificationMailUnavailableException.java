package com.pawmento.member.application.exception;

import com.pawmento.global.exception.StorageUnavailableException;

// 메일 서버가 응답하지 않는다. 엽서 생성 실패처럼 503으로 처리된다 (docs/member/email-verification.md Q2)
public class VerificationMailUnavailableException extends StorageUnavailableException {

	public VerificationMailUnavailableException(Throwable cause) {
		super("인증 메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요.", cause);
	}

}