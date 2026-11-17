package com.pawmento.member.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.pawmento.member.domain.AuthProvider;

interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, Long> {

	Optional<MemberJpaEntity> findByEmail(String email);

	boolean existsByEmail(String email);

	Optional<MemberJpaEntity>findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

}
