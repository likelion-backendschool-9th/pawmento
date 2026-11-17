package com.pawmento.member.application.port.out;

import com.pawmento.member.domain.MemberRole;

// 로그인한 회원의 액세스 토큰 발급. 토큰 형식(JWT)과 서명 방식은 구현이 정한다
public interface TokenIssuer {

	String issue(Long memberId, MemberRole role);

}
