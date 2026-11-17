package com.pawmento.memory.adapter.out.persistence;

import com.pawmento.memory.domain.Memory;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "memories")
public class MemoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long petId;
    private String content;

    public MemoryJpaEntity() {}

    public static MemoryJpaEntity from(Memory memory) {
        MemoryJpaEntity entity = new MemoryJpaEntity();
        entity.petId = memory.getPetId();
        entity.content = memory.getContent();
        return entity;
    }

    public Long getId() {
        return id;
    }
}