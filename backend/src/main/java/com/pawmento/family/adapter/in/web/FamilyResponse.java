package com.pawmento.family.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.pawmento.family.application.port.in.ListFamilyResult;

public record FamilyResponse(Owner owner, List<Member> members) {

    record Owner(Long memberId, String nickname, String tag) {
    }

    record Member(Long memberId, String nickname, String tag, Instant joinedAt) {
    }

    static FamilyResponse from(ListFamilyResult result) {
        ListFamilyResult.Owner owner = result.owner();
        return new FamilyResponse(
                new Owner(owner.memberId(), owner.nickname(), owner.tag()),
                result.members().stream()
                        .map(member -> new Member(member.memberId(), member.nickname(), member.tag(),
                                member.joinedAt()))
                        .toList());
    }
}
