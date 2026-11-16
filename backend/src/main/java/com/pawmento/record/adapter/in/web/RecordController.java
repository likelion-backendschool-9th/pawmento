package com.pawmento.record.adapter.in.web;

import com.pawmento.record.application.port.in.CreateRecordCommand;
import com.pawmento.record.application.port.in.CreateRecordUseCase;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController 
public class RecordController {
    private final CreateRecordUseCase createRecordUseCase; // 다형성, 느슨한 결합

    public RecordController(CreateRecordUseCase createRecordUseCase) {
        this.createRecordUseCase = createRecordUseCase;
    }

    @PostMapping("/records")
    public RecordResponse create(@RequestBody CreateRecordRequest request) {

        CreateRecordCommand command = new CreateRecordCommand(request.getPetId(), request.getContent());
        
        Long id = createRecordUseCase.create(command);

        return new RecordResponse(id);
    }
}
