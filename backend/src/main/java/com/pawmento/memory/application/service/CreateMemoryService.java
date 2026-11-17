package com.pawmento.memory.application.service;

import org.springframework.stereotype.Service;

import com.pawmento.memory.application.port.in.CreateMemoryCommand;
import com.pawmento.memory.application.port.in.CreateMemoryUseCase;
import com.pawmento.memory.application.port.out.SaveMemoryPort;
import com.pawmento.memory.domain.Memory;

@Service 
public class CreateMemoryService implements CreateMemoryUseCase {
    private final SaveMemoryPort saveMemoryPort;

    public CreateMemoryService(SaveMemoryPort saveMemoryPort) {
    this.saveMemoryPort = saveMemoryPort;
}

    public Long create(CreateMemoryCommand command)  {
        Memory memory = Memory.create(command.getPetId(), command.getContent());
        return saveMemoryPort.save(memory);
    }
}
