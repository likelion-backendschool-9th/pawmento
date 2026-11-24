package com.pawmento.family.application.port.out;

import java.time.Instant;

public interface ConsumePetInviteCodePort {

    boolean consume(String codeHash, Instant now);
}
