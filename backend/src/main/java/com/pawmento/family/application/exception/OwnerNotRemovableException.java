package com.pawmento.family.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class OwnerNotRemovableException extends StateConflictException {

    public OwnerNotRemovableException() {
        super("소유주는 가족에서 빠질 수 없습니다. 먼저 소유주를 넘기거나 반려동물을 삭제해 주세요.");
    }

}