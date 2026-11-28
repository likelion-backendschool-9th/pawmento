package com.pawmento.member.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmEmailVerificationRequest(
        @NotBlank(message = "이메일 주소를 확인해 주세요.")
        @Email(message = "이메일 주소를 확인해 주세요.")
        @Size(max = 254, message = "이메일 주소를 확인해 주세요.")
        String email,

        @NotBlank(message = "인증 용도를 확인해 주세요.")
        String purpose,

        @NotBlank(message = "인증번호를 입력해 주세요.")
        String code) {

    @Override
    public String toString() {
        return "ConfirmEmailVerificationRequest[email=" + email + ", purpose=" + purpose + ", code=****]";
    }
}
