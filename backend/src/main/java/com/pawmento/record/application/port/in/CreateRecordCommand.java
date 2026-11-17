package com.pawmento.record.application.port.in;

public class CreateRecordCommand {
    private final Long petId;
    private final String content;

    public CreateRecordCommand(Long petId, String content) {
        this.petId = petId;
        this.content = content;
    }
    
    public Long getPetId() {
        return petId;
    }

    public String getContent() {
        return content;
    }
}
