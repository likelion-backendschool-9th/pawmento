package com.pawmento.member.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pawmento.member.domain.AuthProvider;

interface SocialAccountJpaRepository extends JpaRepository<SocialAccountJpaEntity,Long> {
    Optional<SocialAccountJpaEntity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
