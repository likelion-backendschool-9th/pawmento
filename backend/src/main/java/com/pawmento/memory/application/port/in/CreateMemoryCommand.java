package com.pawmento.memory.application.port.in;

public class CreateMemoryCommand {
    private final Long petId;
    private final String content;

    public CreateMemoryCommand(Long petId, String content) {
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
