package com.pawmento.member.application.port.in;

public interface LoginMemberUseCase {

	// 이메일이 없거나 비밀번호가 틀리면 InvalidCredentialsException
	LoginMemberResult login(LoginMemberCommand command);

}
