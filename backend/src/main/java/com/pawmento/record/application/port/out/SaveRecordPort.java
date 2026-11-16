package com.pawmento.record.application.port.out;

import com.pawmento.record.domain.Record;

public interface SaveRecordPort {
    Long save(Record record);
    
}
