package com.pawmento.member.adapter.out.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pawmento.member.application.service.VerificationCodes;

// 서비스가 Spring 설정을 모르도록, 비밀키는 여기서 읽어 생성자로 넘긴다
@Configuration(proxyBeanMethods = false)
class VerificationCodesConfiguration {

	@Bean
	VerificationCodes verificationCodes(@Value("${pawmento.auth.jwt-secret:}") String secret) {
		return new VerificationCodes(secret);
	}

}