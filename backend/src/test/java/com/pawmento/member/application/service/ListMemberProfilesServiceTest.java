package com.pawmento.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.member.application.port.in.ListMemberProfilesQuery;
import com.pawmento.member.application.port.in.MemberProfileResult;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.domain.Member;
import com.pawmento.member.domain.MemberRole;

class ListMemberProfilesServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");

	private final LoadMemberPort loadMemberPort = mock(LoadMemberPort.class);
	private final ListMemberProfilesService service = new ListMemberProfilesService(loadMemberPort);

	@Test
	@DisplayName("#133-S6 여러 회원을 한 번에 읽어 id·닉네임·태그만 돌려준다")
	void listsProfilesInOneLoad() {
		Member first = Member.restore(1L, "a@pawmento.test", "hash", "다윗", "0427", MemberRole.USER, NOW, null);
		Member second = Member.restore(2L, "b@pawmento.test", "hash", "수민", "1093", MemberRole.USER, NOW, null);
		when(loadMemberPort.loadAllByIds(List.of(1L, 2L, 3L))).thenReturn(List.of(first, second));

		List<MemberProfileResult> result = service.list(new ListMemberProfilesQuery(List.of(1L, 2L, 3L)));

		// 없는 회원(3)은 예외 없이 빠진다
		assertThat(result).containsExactly(
				new MemberProfileResult(1L, "다윗", "0427"),
				new MemberProfileResult(2L, "수민", "1093"));
	}

	@Test
	@DisplayName("#133-S6 빈 목록이면 저장소를 부르지 않는다")
	void emptyQueryDoesNotLoad() {
		assertThat(service.list(new ListMemberProfilesQuery(List.of()))).isEmpty();
		verify(loadMemberPort, never()).loadAllByIds(any());
	}
}
