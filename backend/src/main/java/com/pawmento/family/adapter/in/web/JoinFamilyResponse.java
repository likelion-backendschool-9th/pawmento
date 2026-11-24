package com.pawmento.family.adapter.in.web;

import com.pawmento.family.application.port.in.JoinFamilyResult;

public record JoinFamilyResponse(Long petId) {

    static JoinFamilyResponse from(JoinFamilyResult result) {
        return new JoinFamilyResponse(result.petId());
    }

}
