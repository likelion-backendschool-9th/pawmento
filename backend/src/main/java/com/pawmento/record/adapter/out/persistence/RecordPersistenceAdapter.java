package com.pawmento.record.adapter.out.persistence;

import org.springframework.stereotype.Component;

import com.pawmento.record.application.port.out.ExistsRecordPort;
import com.pawmento.record.application.port.out.SaveRecordPort;
import com.pawmento.record.domain.Record;

@Component
public class RecordPersistenceAdapter implements SaveRecordPort, ExistsRecordPort {

	private final RecordJpaRepository recordJpaRepository;

	public RecordPersistenceAdapter(RecordJpaRepository recordJpaRepository) {
		this.recordJpaRepository = recordJpaRepository;
	}

	@Override
	public Long save(Record record) {
		RecordJpaEntity saved = recordJpaRepository.save(RecordJpaEntity.from(record));
		return saved.getId();
	}

	@Override
	public boolean existsByPetId(Long petId) {
		return recordJpaRepository.existsByPetId(petId);
	}

}
