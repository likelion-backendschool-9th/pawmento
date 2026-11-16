package com.pawmento.record.adapter.in.web;

public class RecordResponse {
    private Long recordId;
    
    public RecordResponse(Long recordId) {
        this.recordId = recordId;
    }

    public Long getRecordId() {
        return recordId;
    }
    
}
