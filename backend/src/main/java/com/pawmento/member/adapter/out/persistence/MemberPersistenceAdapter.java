package com.pawmento.member.adapter.out.persistence;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.port.out.MemberRepository;
import com.pawmento.member.domain.Member;

@Repository
class MemberPersistenceAdapter implements MemberRepository {

	private final MemberJpaRepository jpaRepository;

	MemberPersistenceAdapter(MemberJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Member save(Member member) {
		try {
			// 유니크 제약 위반을 여기서 받도록 바로 flush한다
			return jpaRepository.saveAndFlush(member);
		}
		catch (DataIntegrityViolationException e) {
			// 중복 확인과 저장 사이에 같은 이메일로 가입이 끼어든 경우
			if (isEmailUniqueViolation(e)) {
				throw new DuplicateEmailException();
			}
			throw e;
		}
	}

	@Override
	public Optional<Member> findById(Long id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<Member> findByEmail(String email) {
		return jpaRepository.findByEmail(email);
	}

	@Override
	public boolean existsByEmail(String email) {
		return jpaRepository.existsByEmail(email);
	}

	private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
		return e.getCause() instanceof ConstraintViolationException violation
				&& Member.EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
	}

}
