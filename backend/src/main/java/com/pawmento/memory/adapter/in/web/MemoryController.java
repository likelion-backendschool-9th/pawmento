package com.pawmento.memory.adapter.in.web;

import com.pawmento.memory.application.port.in.CreateMemoryCommand;
import com.pawmento.memory.application.port.in.CreateMemoryUseCase;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController 
public class MemoryController {
    private final CreateMemoryUseCase createMemoryUseCase; // 다형성, 느슨한 결합

    public MemoryController(CreateMemoryUseCase createMemoryUseCase) {
        this.createMemoryUseCase = createMemoryUseCase;
    }

    @PostMapping("/memories")
    public MemoryResponse create(@RequestBody CreateMemoryRequest request) {

        CreateMemoryCommand command = new CreateMemoryCommand(request.getPetId(), request.getContent());
        
        Long id = createMemoryUseCase.create(command);

        return new MemoryResponse(id);
    }
}
