package com.pawmento.member.application.port.in;

public interface SignUpMemberUseCase {
	MemberResult signUp(SignUpMemberCommand command);
}
