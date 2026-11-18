package com.pawmento.memory.application.port.out;

import com.pawmento.memory.domain.Memory;

public interface SaveMemoryPort {
    Long save(Memory memory);
    
}
