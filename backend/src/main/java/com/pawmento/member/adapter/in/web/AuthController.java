package com.pawmento.member.adapter.in.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.member.adapter.in.web.dto.LoginRequest;
import com.pawmento.member.adapter.in.web.dto.SignUpRequest;
import com.pawmento.member.application.dto.LoginResult;
import com.pawmento.member.application.dto.MemberResponse;
import com.pawmento.member.application.port.in.MemberUseCase;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

// 로그인 없이 부르는 경로다 (global.security.PublicEndpoints)
@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final MemberUseCase memberUseCase;

	private final AccessTokenCookie accessTokenCookie;

	AuthController(MemberUseCase memberUseCase, AccessTokenCookie accessTokenCookie) {
		this.memberUseCase = memberUseCase;
		this.accessTokenCookie = accessTokenCookie;
	}

	// 가입만 하고 로그인 상태로 만들지는 않는다. 이어서 로그인을 부른다
	@PostMapping("/signup")
	ResponseEntity<MemberResponse> signUp(@Valid @RequestBody SignUpRequest request) {
		MemberResponse member = memberUseCase.signUp(request.email(), request.password(), request.nickname());
		return ResponseEntity.status(HttpStatus.CREATED).body(member);
	}

	// 토큰은 응답 본문에 싣지 않고 HttpOnly 쿠키로만 내린다
	@PostMapping("/login")
	ResponseEntity<MemberResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		LoginResult result = memberUseCase.login(request.email(), request.password());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, accessTokenCookie.issue(result.accessToken(), httpRequest).toString())
			.body(result.member());
	}

	// 쿠키를 지운다. 서버는 토큰을 저장하지 않으므로 이미 발급된 토큰 자체는 만료 시간까지 유효하다
	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, accessTokenCookie.expire(httpRequest).toString())
			.build();
	}

}
