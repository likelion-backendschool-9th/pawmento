package com.pawmento.member.adapter.out.persistence;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.port.out.ExistsMemberPort;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.domain.AuthProvider;
import com.pawmento.member.domain.Member;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class MemberPersistenceAdapter implements LoadMemberPort, SaveMemberPort, ExistsMemberPort {

	private final MemberJpaRepository jpaRepository;

	@Override
	public Member save(Member member) {
		try {
			// 유니크 제약 위반을 여기서 받도록 바로 flush한다
			return jpaRepository.saveAndFlush(MemberJpaEntity.from(member)).toDomain();
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
	public Optional<Member> loadById(Long id) {
		return jpaRepository.findById(id).map(MemberJpaEntity::toDomain);
	}

	@Override
	public Optional<Member> loadByEmail(String email) {
		return jpaRepository.findByEmail(email).map(MemberJpaEntity::toDomain);
	}

	@Override
	public boolean existsByEmail(String email) {
		return jpaRepository.existsByEmail(email);
	}

	@Override
	public Optional<Member> loadForNicknameChange(Long memberId) {
		return jpaRepository.findForNicknameChange(memberId).map(MemberJpaEntity::toDomain);
	}

	@Override
	public Optional<Member> loadByProvider(AuthProvider provider, String providerUserId) {
		return jpaRepository.findByProviderAndProviderUserId(provider, providerUserId).map(MemberJpaEntity::toDomain);
	}

	private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
		return e.getCause() instanceof ConstraintViolationException violation
				&& MemberJpaEntity.EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
	}

}
