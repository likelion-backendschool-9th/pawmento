package com.pawmento.family.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class FamilyPetNotFoundException extends ResourceNotFoundException {

    public FamilyPetNotFoundException() {
        super("반려동물을 찾을 수 없습니다.");
    }

}
