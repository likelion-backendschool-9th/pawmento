package com.pawmento.pet.application.port.in;

// 소유주와 모드는 같은 반려동물 스냅샷에서 읽는다. 역할 판정은 family 입력 포트가 맡는다.
public record GetPetOwnerResult(Long ownerMemberId, String mode) {}
