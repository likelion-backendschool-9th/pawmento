package com.pawmento.memory.adapter.out.persistence;

import org.springframework.stereotype.Component;

import com.pawmento.memory.application.port.out.SaveMemoryPort;
import com.pawmento.memory.domain.Memory;

@Component 
public class MemoryPersistenceAdapter implements SaveMemoryPort {
    private final MemoryJpaRepository memoryJpaRepository;

    public MemoryPersistenceAdapter(MemoryJpaRepository memoryJpaRepository) {
    this.memoryJpaRepository = memoryJpaRepository;
}

    @Override 
    public Long save(Memory memory) {
        MemoryJpaEntity entity = MemoryJpaEntity.from(memory);
        MemoryJpaEntity saved = memoryJpaRepository.save(entity);
        return saved.getId();
    }
    
}
