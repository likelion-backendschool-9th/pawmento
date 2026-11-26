package com.pawmento.member.adapter.in.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.member.application.port.in.SendEmailVerificationCommand;
import com.pawmento.member.application.port.in.SendEmailVerificationUseCase;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;




@RestController
@RequestMapping("/auth/email-verifications")
@RequiredArgsConstructor
class EmailVerificationController {

    private final SendEmailVerificationUseCase sendEmailVerificationUseCase;

    @PostMapping
    ResponseEntity<EmailVerificationResponse> send(@Valid  @RequestBody  SendEmailVerificationRequest request) {
        EmailVerificationResponse response = EmailVerificationResponse.from(sendEmailVerificationUseCase
            .send(new SendEmailVerificationCommand(request.email(), request.purpose())));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
