package com.pawmento.postcard.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface PostcardJpaRepository extends JpaRepository<PostcardJpaEntity, Long> {}
