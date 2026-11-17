package com.pawmento.record.adapter.out.persistence;

import org.springframework.stereotype.Component;

import com.pawmento.record.application.port.out.SaveRecordPort;
import com.pawmento.record.domain.Record;

@Component 
public class RecordPersistenceAdapter implements SaveRecordPort {
    private final RecordJpaRepository recordJpaRepository;

    public RecordPersistenceAdapter(RecordJpaRepository recordJpaRepository) {
    this.recordJpaRepository = recordJpaRepository;
}

    @Override 
    public Long save(Record record) {
        RecordJpaEntity entity = RecordJpaEntity.from(record);
        RecordJpaEntity saved = recordJpaRepository.save(entity);
        return saved.getId();
    }
    
}
