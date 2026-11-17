package com.pawmento.member.application.port.in;

public interface GetMemberUseCase {

	// 회원이 없으면 MemberNotFoundException
	GetMemberResult get(GetMemberQuery query);

}
