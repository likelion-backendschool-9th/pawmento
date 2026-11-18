package com.pawmento.member.application.port.in;

public interface SocialLoginMemberUseCase {
    
    // 처음 로그인하면 가입도 함께 한다.
	// 지원하지 않는 제공자면 UnsupportedSocialProviderException,
	// 같은 이메일의 다른 가입 방식 회원이 있으면 SocialEmailConflictException,
	// 제공자 인증에 실패하면 SocialLoginFailedException
    LoginMemberResult login(SocialLoginMemberCommand command);
}
