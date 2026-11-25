package com.pawmento.family.adapter.in.web;

import com.pawmento.family.application.port.in.JoinFamilyResult;

public record JoinFamilyResponse(Long petId, String name, String tag) {

    static JoinFamilyResponse from(JoinFamilyResult result) {
        return new JoinFamilyResponse(result.petId(), result.name(), result.tag());
    }

}
