package com.pawmento.map.application.port.out;

import java.util.Optional;

import com.pawmento.map.domain.RecordLocation;

public interface LoadRecordLocationPort {

	Optional<RecordLocation> loadByRecordId(Long recordId);

}
