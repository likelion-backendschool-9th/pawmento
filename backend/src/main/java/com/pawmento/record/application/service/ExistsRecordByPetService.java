package com.pawmento.record.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.record.application.port.in.ExistsRecordByPetQuery;
import com.pawmento.record.application.port.in.ExistsRecordByPetUseCase;
import com.pawmento.record.application.port.out.ExistsRecordPort;

@Service
public class ExistsRecordByPetService implements ExistsRecordByPetUseCase {

	private final ExistsRecordPort existsRecordPort;

	public ExistsRecordByPetService(ExistsRecordPort existsRecordPort) {
		this.existsRecordPort = existsRecordPort;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean exists(ExistsRecordByPetQuery query) {
		return existsRecordPort.existsByPetId(query.petId());
	}

}
