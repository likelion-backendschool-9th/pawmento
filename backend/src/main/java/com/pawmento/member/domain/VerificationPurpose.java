package com.pawmento.member.domain;

// 인증번호를 어디에 쓰는지. 같은 이메일이라도 용도가 다르면 따로 보낸다 (docs/member/email-verification.md)
public enum VerificationPurpose {
	SIGNUP,
	PASSWORD_RESET,
	SET_PASSWORD
}