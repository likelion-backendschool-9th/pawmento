package com.pawmento.member.application.port.out;

public interface ExistsMemberPort {

    boolean existsByEmail(String email);
}
