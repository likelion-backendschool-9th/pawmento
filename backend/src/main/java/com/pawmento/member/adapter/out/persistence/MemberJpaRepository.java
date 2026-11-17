package com.pawmento.member.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, Long> {

	Optional<MemberJpaEntity> findByEmail(String email);

	boolean existsByEmail(String email);

}
