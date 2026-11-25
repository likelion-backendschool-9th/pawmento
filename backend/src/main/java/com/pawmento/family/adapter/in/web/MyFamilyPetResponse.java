package com.pawmento.family.adapter.in.web;

import java.time.Instant;

import com.pawmento.family.application.port.in.MyFamilyPetResult;

public record MyFamilyPetResponse(Long petId, String name, String tag, Instant joinedAt) {

    static MyFamilyPetResponse from(MyFamilyPetResult result) {
        return new MyFamilyPetResponse(result.petId(), result.name(), result.tag(), result.joinedAt());
    }
}
