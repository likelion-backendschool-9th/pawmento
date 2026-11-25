package com.pawmento.family.adapter.in.web;

import java.time.Instant;

import com.pawmento.family.application.port.in.IssueInviteCodeResult;

public record InviteCodeResponse(String code, Instant expiresAt) {

    static InviteCodeResponse from(IssueInviteCodeResult result) {
        return new InviteCodeResponse(result.code(), result.expiresAt());
    }

}
