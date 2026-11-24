package com.pawmento.family.application.port.in;

import java.time.Instant;
import java.util.List;

public record ListFamilyResult(Owner owner, List<Family> members) {

    // 회원 정보를 찾지 못하면 nickname·tag는 null이다
    public record Owner(Long memberId, String nickname, String tag) {
    }

    public record Family(Long memberId, String nickname, String tag, Instant joinedAt) {
    }
}