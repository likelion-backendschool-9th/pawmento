package com.pawmento.record.application.service;

import org.springframework.stereotype.Service;

import com.pawmento.record.application.port.in.CreateRecordCommand;
import com.pawmento.record.application.port.in.CreateRecordUseCase;
import com.pawmento.record.application.port.out.SaveRecordPort;
import com.pawmento.record.domain.Record;

@Service 
public class CreateRecordService implements CreateRecordUseCase {
    private final SaveRecordPort saveRecordPort;

    public CreateRecordService(SaveRecordPort saveRecordPort) {
    this.saveRecordPort = saveRecordPort;
}

    public Long create(CreateRecordCommand command)  {
        Record record = Record.create(command.getPetId(), command.getContent());
        return saveRecordPort.save(record);
    }
}
