package com.pawmento.memory.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryJpaRepository extends JpaRepository<MemoryJpaEntity, Long> {
    
}
