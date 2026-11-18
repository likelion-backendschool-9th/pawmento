package com.pawmento.map.adapter.in.web;

import jakarta.validation.constraints.NotNull;

record StartWalkRequest(@NotNull(message = "petId를 입력해 주세요.") Long petId) {
}
