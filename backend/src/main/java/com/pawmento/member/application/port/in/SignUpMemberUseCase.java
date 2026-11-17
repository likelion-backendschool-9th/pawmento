package com.pawmento.member.application.port.in;

public interface SignUpMemberUseCase {

	// 이메일이 이미 있으면 DuplicateEmailException
	SignUpMemberResult signUp(SignUpMemberCommand command);

}
