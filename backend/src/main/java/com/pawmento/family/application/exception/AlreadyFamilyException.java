package com.pawmento.family.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class AlreadyFamilyException extends StateConflictException {

    public AlreadyFamilyException() {
        super("이미 이 반려동물의 소유주나 가족입니다.");
    }

}
