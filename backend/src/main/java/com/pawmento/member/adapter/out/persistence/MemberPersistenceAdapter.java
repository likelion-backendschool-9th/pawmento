package com.pawmento.member.adapter.out.persistence;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.exception.DuplicateMemberTagException;
import com.pawmento.member.application.exception.DuplicateSocialAccountException;
import com.pawmento.member.application.port.out.ExistsMemberPort;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.domain.AuthProvider;
import com.pawmento.member.domain.Member;
import com.pawmento.member.domain.SocialAccount;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class MemberPersistenceAdapter implements LoadMemberPort, SaveMemberPort, ExistsMemberPort {

	private final MemberJpaRepository jpaRepository;

	private final SocialAccountJpaRepository socialAccountRepository;

	@Override
	public Member save(Member member) {
		try {
			// 유니크 제약 위반을 여기서 받도록 바로 flush한다
			return jpaRepository.saveAndFlush(MemberJpaEntity.from(member)).toDomain();
		}
		catch (DataIntegrityViolationException e) {
			throw translate(e);
		}
	}

	// 서비스에는 제공자 HTTP 호출이 있어 트랜잭션을 걸지 않으므로, 회원과 소셜 계정은 여기서 함께 묶는다.
	// 둘 중 하나라도 실패하면 둘 다 남지 않는다 (#109-S18)
	@Override
	@Transactional
	public Member saveSocial(Member member, AuthProvider provider, String providerUserId) {
		try {
			Member saved = jpaRepository.saveAndFlush(MemberJpaEntity.from(member)).toDomain();
			SocialAccount account = SocialAccount.link(saved.getId(), provider, providerUserId, saved.getCreatedAt());
			socialAccountRepository.saveAndFlush(SocialAccountJpaEntity.from(account));
			return saved;
		}
		catch (DataIntegrityViolationException e) {
			throw translate(e);
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
	public boolean existsByNicknameAndTag(String nickname, String tag) {
		return jpaRepository.existsByNicknameAndTag(nickname, tag);
	}

	@Override
	public Optional<Member> loadForNicknameChange(Long memberId) {
		return jpaRepository.findForNicknameChange(memberId).map(MemberJpaEntity::toDomain);
	}

	// 소셜 계정을 먼저 찾고, 그 계정이 가리키는 회원을 읽는다
	@Override
	public Optional<Member> loadByProvider(AuthProvider provider, String providerUserId) {
		return socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
				.flatMap(account -> jpaRepository.findById(account.getMemberId()))
				.map(MemberJpaEntity::toDomain);
	}

	// 중복 확인과 저장 사이에 같은 이메일이나 같은 소셜 계정의 가입이 끼어든 경우를 제약 이름으로 나눈다
	private RuntimeException translate(DataIntegrityViolationException e) {
		if (isViolation(e, MemberJpaEntity.EMAIL_UNIQUE_CONSTRAINT)) {
			return new DuplicateEmailException();
		}
		if (isViolation(e, SocialAccountJpaEntity.PROVIDER_USER_UNIQUE_CONSTRAINT)) {
			return new DuplicateSocialAccountException();
		}
		if (isViolation(e, MemberJpaEntity.NICKNAME_TAG_UNIQUE_CONSTRAINT)) {
			return new DuplicateMemberTagException();
		}
		return e;
	}

	private boolean isViolation(DataIntegrityViolationException e, String constraintName) {
		return e.getCause() instanceof ConstraintViolationException violation
				&& constraintName.equalsIgnoreCase(violation.getConstraintName());
	}
}