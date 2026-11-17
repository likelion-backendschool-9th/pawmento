package com.pawmento.member.application.port.out;

import com.pawmento.member.domain.Member;

public interface SaveMemberPort {

	// 이메일이 이미 있으면 DuplicateEmailException을 던진다. 반환하는 회원에는 새로 생긴 id가 있다
	Member save(Member member);

}
