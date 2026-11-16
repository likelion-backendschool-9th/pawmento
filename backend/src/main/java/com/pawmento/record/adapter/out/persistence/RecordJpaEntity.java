package com.pawmento.record.adapter.out.persistence;

import com.pawmento.record.domain.Record;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "records")
public class RecordJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long petId;
    private String content;

    public RecordJpaEntity() {}

    public static RecordJpaEntity from(Record record) {
        RecordJpaEntity entity = new RecordJpaEntity();
        entity.petId = record.getPetId();
        entity.content = record.getContent();
        return entity;
    }

    public Long getId() {
        return id;
    }
}