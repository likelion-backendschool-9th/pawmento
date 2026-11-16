package com.pawmento.record.adapter.in.web;

public class CreateRecordRequest {
    private Long petId;
    private String content;

    public CreateRecordRequest() {}

    public Long getPetId() {
        return petId;
    }

    public String getContent() {
        return content;
    }
    
}
