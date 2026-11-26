package com.pawmento.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.exception.DuplicateMemberTagException;
import com.pawmento.member.application.exception.DuplicateSocialAccountException;
import com.pawmento.member.application.exception.SocialLoginFailedException;
import com.pawmento.member.application.exception.UnsupportedSocialProviderException;
import com.pawmento.member.application.port.in.LoginMemberResult;
import com.pawmento.member.application.port.in.SocialLoginMemberCommand;
import com.pawmento.member.application.port.out.ExistsMemberPort;
import com.pawmento.member.application.port.out.IssueTokenPort;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.application.port.out.LoadSocialProfilePort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.application.port.out.SocialProfile;
import com.pawmento.member.domain.AuthProvider;
import com.pawmento.member.domain.Member;
import com.pawmento.member.domain.MemberRole;

class SocialLoginMemberServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-18T03:00:00Z");

	private static final String REDIRECT_URI = "http://localhost:3000/auth/callback/google";

	private final LoadSocialProfilePort socialProfiles = mock(LoadSocialProfilePort.class);

	private final LoadMemberPort loadMember = mock(LoadMemberPort.class);

	private final ExistsMemberPort existsMember = mock(ExistsMemberPort.class);

	private final SaveMemberPort saveMember = mock(SaveMemberPort.class);

	private final IssueTokenPort tokenIssuer = mock(IssueTokenPort.class);

    private final MemberTagGenerator tags = mock(MemberTagGenerator.class);

	private final SocialLoginMemberService service = new SocialLoginMemberService(socialProfiles, loadMember,
			existsMember, saveMember, tokenIssuer, Clock.fixed(NOW, ZoneOffset.UTC), tags);

	@BeforeEach
	void setUp() {
		when(tokenIssuer.issue(any(), any())).thenReturn("token");
        when(tags.next(any())).thenReturn("0427");
	}

	@Test
	@DisplayName("#40-S2 이름이 없으면 '보호자', 21자 이상이면 앞 20자로 가입한다")
	void nicknameIsAdjusted() {
		when(loadMember.loadByProvider(any(), any())).thenReturn(Optional.empty());
		when(saveMember.saveSocial(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

		googleReturns("dawit@gmail.com", null);
		assertThat(service.login(google()).member().nickname()).isEqualTo("보호자");

		googleReturns("dawit@gmail.com", "가".repeat(25));
		assertThat(service.login(google()).member().nickname()).isEqualTo("가".repeat(20));
	}

	@Test
	@DisplayName("#40-S4 구글의 이메일이 바뀌어도 같은 sub이면 같은 회원으로 로그인한다")
	void changedEmailStillSameMember() {
		googleReturns("new@gmail.com", "다윗");
		when(loadMember.loadByProvider(AuthProvider.GOOGLE, "g-123"))
				.thenReturn(Optional.of(googleMember(7L, "dawit@gmail.com")));

		assertThat(service.login(google()).member().id()).isEqualTo(7L);
		verify(existsMember, never()).existsByEmail(any());
	}

	@Test
	@DisplayName("#40-S8 제공자가 확인된 이메일을 주지 않으면 가입하지 않는다")
	void noEmailFails() {
		when(loadMember.loadByProvider(AuthProvider.GOOGLE, "g-123")).thenReturn(Optional.empty());

		googleReturns(null, "다윗");
		assertThatThrownBy(() -> service.login(google())).isInstanceOf(SocialLoginFailedException.class);

		googleReturns(" ", "다윗");
		assertThatThrownBy(() -> service.login(google())).isInstanceOf(SocialLoginFailedException.class);

		verify(saveMember, never()).saveSocial(any(), any(), any());
	}

	@Test
	@DisplayName("#40-S9 지원하지 않는 제공자와 local은 제공자를 부르기 전에 거부한다")
	void unsupportedProviderIsRejected() {
		for (String provider : new String[] { "facebook", "local", "" }) {
			assertThatThrownBy(() -> service.login(new SocialLoginMemberCommand(provider, "abc", null, REDIRECT_URI)))
					.isInstanceOf(UnsupportedSocialProviderException.class);
		}
		verify(socialProfiles, never()).load(any(), any(), any(), any());
	}

	@Test
	@DisplayName("#40-S15 같은 구글 계정의 첫 로그인이 동시에 와서 저장에 지면 먼저 저장된 회원으로 로그인한다")
	void concurrentSameAccountLogsInAsWinner() {
		googleReturns("dawit@gmail.com", "다윗");
		when(loadMember.loadByProvider(AuthProvider.GOOGLE, "g-123"))
				.thenReturn(Optional.empty(), Optional.of(googleMember(7L, "dawit@gmail.com")));
		when(saveMember.saveSocial(any(), any(), any())).thenThrow(new DuplicateSocialAccountException());

		assertThat(service.login(google()).member().id()).isEqualTo(7L);
	}

	@Test
	@DisplayName("#40-S15 이메일 확인 때 같은 구글 계정이 먼저 가입했으면 409가 아니라 그 회원으로 로그인한다")
	void concurrentSameAccountFoundAtEmailCheck() {
		googleReturns("dawit@gmail.com", "다윗");
		when(loadMember.loadByProvider(AuthProvider.GOOGLE, "g-123"))
				.thenReturn(Optional.empty(), Optional.of(googleMember(7L, "dawit@gmail.com")));
		when(existsMember.existsByEmail("dawit@gmail.com")).thenReturn(true);

		assertThat(service.login(google()).member().id()).isEqualTo(7L);
		verify(saveMember, never()).saveSocial(any(), any(), any());
	}

	@Test
	@DisplayName("#40-S16 같은 이메일의 다른 가입이 먼저 저장되면 409 그대로 실패한다")
	void concurrentSameEmailFails() {
		googleReturns("dawit@gmail.com", "다윗");
		when(loadMember.loadByProvider(AuthProvider.GOOGLE, "g-123")).thenReturn(Optional.empty());
		when(saveMember.saveSocial(any(), any(), any())).thenThrow(new DuplicateEmailException());

		assertThatThrownBy(() -> service.login(google())).isInstanceOf(DuplicateEmailException.class);
		verify(tokenIssuer, never()).issue(any(), any());
	}

	@Test
	@DisplayName("#137-S10 소셜 가입 저장이 태그 충돌로 지면 새 태그로 다시 저장한다")
	void retriesWithNewTagOnTagConflict() {
		googleReturns("dawit@gmail.com", "다윗");
		when(loadMember.loadByProvider(AuthProvider.GOOGLE, "g-123")).thenReturn(Optional.empty());
		when(tags.next("다윗")).thenReturn("0427", "0581");
		when(saveMember.saveSocial(any(), any(), any()))
				.thenThrow(new DuplicateMemberTagException())
				.thenAnswer(invocation -> invocation.getArgument(0));

		LoginMemberResult result = service.login(google());

		assertThat(result.member().tag()).isEqualTo("0581");
		verify(saveMember, times(2)).saveSocial(any(), any(), any());
	}

	private void googleReturns(String email, String name) {
		when(socialProfiles.load(AuthProvider.GOOGLE, "abc", null, REDIRECT_URI))
				.thenReturn(new SocialProfile(AuthProvider.GOOGLE, "g-123", email, name));
	}

	private SocialLoginMemberCommand google() {
		return new SocialLoginMemberCommand("google", "abc", null, REDIRECT_URI);
	}

	private Member googleMember(Long id, String email) {
		return Member.restore(id, email, null, "다윗", "0427", MemberRole.USER, NOW, null);
	}

}