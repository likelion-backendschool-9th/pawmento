package com.pawmento.member.application.port.out;

public interface DeleteEmailVerificationPort {
    boolean delete(Long id, String codeHash);
}
