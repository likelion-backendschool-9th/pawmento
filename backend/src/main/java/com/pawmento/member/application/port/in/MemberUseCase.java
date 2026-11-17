package com.pawmento.member.application.port.in;

import com.pawmento.member.application.dto.LoginResult;
import com.pawmento.member.application.dto.MemberResponse;

// 회원 기능의 입구. 웹 어댑터(컨트롤러)는 이 인터페이스로만 애플리케이션 계층을 부른다
public interface MemberUseCase {

	// 이메일이 이미 있으면 DuplicateEmailException
	MemberResponse signUp(String email, String rawPassword, String nickname);

	// 이메일이 없거나 비밀번호가 틀리면 InvalidCredentialsException
	LoginResult login(String email, String rawPassword);

	// 회원이 없으면 MemberNotFoundException
	MemberResponse getMember(Long memberId);

	// 달력월(Asia/Seoul) 한도 초과면 NicknameChangeLimitException
	MemberResponse changeNickname(Long memberId, String nickname);

}
