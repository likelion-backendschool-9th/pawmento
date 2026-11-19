package com.pawmento.member.adapter.in.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.member.application.port.in.LoginMemberCommand;
import com.pawmento.member.application.port.in.SocialLoginMemberCommand;
import com.pawmento.member.application.port.in.LoginMemberResult;
import com.pawmento.member.application.port.in.LoginMemberUseCase;
import com.pawmento.member.application.port.in.SignUpMemberCommand;
import com.pawmento.member.application.port.in.SignUpMemberUseCase;
import com.pawmento.member.application.port.in.SocialLoginMemberUseCase;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

// 로그인 없이 부르는 경로다 (global.security.PublicEndpoints)
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
class AuthController {

	private final SignUpMemberUseCase signUpMemberUseCase;
	
	private final LoginMemberUseCase loginMemberUseCase;
	
	private final SocialLoginMemberUseCase socialLoginMemberUseCase;
	
	private final AccessTokenCookie accessTokenCookie;

	// 가입만 하고 로그인 상태로 만들지는 않는다. 이어서 로그인을 부른다
	@PostMapping("/signup")
	ResponseEntity<MemberResponse> signUp(@Valid @RequestBody SignUpMemberRequest request) {
		MemberResponse member = MemberResponse.from(signUpMemberUseCase
				.signUp(new SignUpMemberCommand(request.email(), request.password(), request.nickname())));
		return ResponseEntity.status(HttpStatus.CREATED).body(member);
	}

	// 토큰은 응답 본문에 싣지 않고 HttpOnly 쿠키로만 내린다
	@PostMapping("/login")
	ResponseEntity<MemberResponse> login(@Valid @RequestBody LoginMemberRequest request, HttpServletRequest httpRequest) {
		LoginMemberResult result = loginMemberUseCase
				.login(new LoginMemberCommand(request.email(), request.password()));
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, accessTokenCookie.issue(result.accessToken(), httpRequest).toString())
				.body(MemberResponse.from(result.member()));
	}

	// 처음 로그인하면 가입도 함께 된다. 토큰은 로그인과 같이 HttpOnly 쿠키로만 내린다.
	// 지원하지 않는 provider는 400, 다른 방법으로 가입된 이메일은 409, 제공자 인증 실패는 401
	@PostMapping("/oauth/{provider}")
	ResponseEntity<MemberResponse> socialLogin(@PathVariable String provider,
			@Valid @RequestBody SocialLoginMemberRequest request, HttpServletRequest httpRequest) {
		LoginMemberResult result = socialLoginMemberUseCase.login(
			new SocialLoginMemberCommand(provider, request.code(), request.state(), request.redirectUri()));
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, accessTokenCookie.issue(result.accessToken(), httpRequest).toString())
				.body(MemberResponse.from(result.member()));
	}

	// 쿠키를 지운다. 서버는 토큰을 저장하지 않으므로 이미 발급된 토큰 자체는 만료 시간까지 유효하다.
	// 본문은 없지만 Content-Type: application/json을 요구한다. 다른 사이트의 HTML 폼은 이 형식으로 보낼 수 없어,
	// 폼을 자동 제출시켜 사용자를 강제로 로그아웃시키는 요청이 415로 거부된다
	@PostMapping(path = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, accessTokenCookie.expire(httpRequest).toString()).build();
	}

}
