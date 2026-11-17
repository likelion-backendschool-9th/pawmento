package com.pawmento.memory.adapter.in.web;

public class MemoryResponse {
    private Long memoryId;
    
    public MemoryResponse(Long memoryId) {
        this.memoryId = memoryId;
    }

    public Long getMemoryId() {
        return memoryId;
    }
    
}
