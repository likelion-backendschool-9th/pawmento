package com.pawmento.member.application.port.out;

import java.util.Optional;

import com.pawmento.member.domain.Member;

public interface LoadMemberPort {
	Optional<Member> findById(Long id);

	Optional<Member> findByEmail(String email);

	boolean existsByEmail(String email);

	// 변경 확정까지 같은 회원의 닉네임 변경이 경합하지 않도록 조회한다.
	Optional<Member> findForNicknameChange(Long memberId);
}
