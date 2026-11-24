package com.pawmento.member.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class DuplicateMemberTagException extends StateConflictException {

    public DuplicateMemberTagException() {
        super("잠시 후 다시 시도해 주세요.");
    }
}
