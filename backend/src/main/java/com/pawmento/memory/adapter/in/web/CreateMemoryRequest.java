package com.pawmento.memory.adapter.in.web;

public class CreateMemoryRequest {
    private Long petId;
    private String content;

    public CreateMemoryRequest() {}

    public Long getPetId() {
        return petId;
    }

    public String getContent() {
        return content;
    }
    
}
