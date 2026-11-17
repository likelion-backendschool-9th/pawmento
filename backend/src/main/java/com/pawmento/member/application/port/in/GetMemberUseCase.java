package com.pawmento.member.application.port.in;

public interface GetMemberUseCase {
	MemberResult get(GetMemberQuery query);
}
