package com.pawmento.family.application.port.in;

import java.time.Instant;

// 원래 코드는 이 응답에서만 한 번 보여준다. DB에는 해시만 남는다
public record IssueInviteCodeResult(String code, Instant expiresAt) {
}
