package com.pawmento.member.application.port.out;

import java.util.Optional;

import com.pawmento.member.domain.Member;

// 회원 저장소. 구현은 adapter.out.persistence에 있다
public interface MemberRepository {

	// 이메일이 이미 있으면 DuplicateEmailException을 던진다
	Member save(Member member);

	Optional<Member> findById(Long id);

	// email은 Member.normalizeEmail을 거친 값이어야 한다
	Optional<Member> findByEmail(String email);

	boolean existsByEmail(String email);

}
