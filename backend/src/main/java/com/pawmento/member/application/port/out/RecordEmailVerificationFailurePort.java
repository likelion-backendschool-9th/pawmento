package com.pawmento.member.application.port.out;

public interface RecordEmailVerificationFailurePort {
    boolean recordFailure(Long id, String sendId);
}
