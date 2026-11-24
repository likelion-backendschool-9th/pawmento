package com.pawmento.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.pawmento.member.application.port.out.ExistsMemberPort;

class MemberTagGeneratorTest {
	private final ExistsMemberPort exists = mock(ExistsMemberPort.class);
	private final MemberTagGenerator generator = new MemberTagGenerator(exists);

	@Test
	@DisplayName("#137-S1 태그는 앞자리 0을 포함한 숫자 4자리다")
	void tagIsFourDigits() {
		when(exists.existsByNicknameAndTag(any(), any())).thenReturn(false);
		for (int i = 0; i < 200; i++) {
			assertThat(generator.next("다윗")).matches("\\d{4}");
		}
	}

	@Test
	@DisplayName("#137-S6 같은 닉네임에 이미 있는 태그가 나오면 다시 뽑는다")
	void redrawsWhenTaken() {
		when(exists.existsByNicknameAndTag(eq("다윗"), any())).thenReturn(true, false);

		String tag = generator.next("다윗");

		ArgumentCaptor<String> checked = ArgumentCaptor.forClass(String.class);
		verify(exists, times(2)).existsByNicknameAndTag(eq("다윗"), checked.capture());
		assertThat(tag).isEqualTo(checked.getAllValues().get(1));
	}

	@Test
	@DisplayName("#137-S6 닉네임을 바꿀 때 이전 태그와 같은 값은 주지 않는다")
	void neverReturnsPreviousTag() {
		when(exists.existsByNicknameAndTag(any(), any())).thenReturn(false);
		for (int i = 0; i < 2000; i++) {
			assertThat(generator.next("다윗", "0427")).isNotEqualTo("0427");
		}
	}
}
