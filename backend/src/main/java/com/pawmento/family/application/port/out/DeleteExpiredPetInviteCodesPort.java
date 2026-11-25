package com.pawmento.family.application.port.out;

import java.time.Instant;

public interface DeleteExpiredPetInviteCodesPort {

    // 그 반려동물의 코드 중 now 시점에 이미 만료된 것을 지운다
    int deleteExpired(Long petId, Instant now);
}