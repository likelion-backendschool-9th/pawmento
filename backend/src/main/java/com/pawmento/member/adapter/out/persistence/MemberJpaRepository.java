package com.pawmento.member.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pawmento.member.domain.AuthProvider;

import jakarta.persistence.LockModeType;

interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, Long> {
	Optional<MemberJpaEntity> findByEmail(String email);

	boolean existsByEmail(String email);

	Optional<MemberJpaEntity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

	// 같은 회원의 월 1회 변경 요청을 직렬화한다. 잠금은 영속성 어댑터 안에서만 안다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from MemberJpaEntity m where m.id = :id")
	Optional<MemberJpaEntity> findForNicknameChange(@Param("id") Long id);
}
