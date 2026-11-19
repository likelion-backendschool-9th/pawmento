package com.pawmento.media.application.port.in;

// 호출하는 부모 도메인이 현재 열람 권한과 실제 연결 관계를 먼저 확인한다.
public record GetAttachedMediaQuery(Long petId, Long assetId, boolean thumbnail) {}
