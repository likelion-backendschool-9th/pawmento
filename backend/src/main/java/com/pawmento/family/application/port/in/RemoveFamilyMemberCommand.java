package com.pawmento.family.application.port.in;

// 소유주(requesterId)가 가족(targetMemberId)을 내보낸다
public record RemoveFamilyMemberCommand(Long requesterId, Long petId, Long targetMemberId) {
}