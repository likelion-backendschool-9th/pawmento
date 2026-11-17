package com.pawmento.member.application.port.in;

public interface ChangeMemberNicknameUseCase {
	MemberResult changeNickname(ChangeMemberNicknameCommand command);
}
