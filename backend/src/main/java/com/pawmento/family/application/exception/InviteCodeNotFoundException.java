package com.pawmento.family.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class InviteCodeNotFoundException extends ResourceNotFoundException {

    public InviteCodeNotFoundException() {
        super("사용 할 수 없는 초대 코드입니다.");
    }

}
