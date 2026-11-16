package com.pawmento.record.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordJpaRepository extends JpaRepository<RecordJpaEntity, Long> {
    
}
