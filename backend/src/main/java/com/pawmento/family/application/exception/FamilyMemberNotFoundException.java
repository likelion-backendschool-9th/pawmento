package com.pawmento.family.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class FamilyMemberNotFoundException extends ResourceNotFoundException {

    public FamilyMemberNotFoundException() {
        super("이 반려동물의 가족이 아닙니다.");
    }

}