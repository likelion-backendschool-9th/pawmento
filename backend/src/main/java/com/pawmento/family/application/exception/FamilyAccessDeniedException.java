package com.pawmento.family.application.exception;

import com.pawmento.global.exception.PermissionDeniedException;

public class FamilyAccessDeniedException extends PermissionDeniedException {

    public FamilyAccessDeniedException() {
        super("이 반려동물의 소유주나 가족만 할 수 있습니다.");
    }

}
