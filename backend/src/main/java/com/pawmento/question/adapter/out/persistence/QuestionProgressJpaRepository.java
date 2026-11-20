package com.pawmento.question.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface QuestionProgressJpaRepository
    extends JpaRepository<QuestionProgressJpaEntity, QuestionProgressId> {}
