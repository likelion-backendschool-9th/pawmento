package com.pawmento.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import tools.jackson.databind.json.JsonMapper;

// 시나리오는 docs/member/auth-base.md에 있다.
// 역할로 막는 경로가 아직 없어 요청으로는 403을 만들 수 없다. 응답을 만드는 부분만 확인한다
class SecurityErrorResponderTest {

	private final SecurityErrorResponder responder = new SecurityErrorResponder(JsonMapper.builder().build());

	@Test
	@DisplayName("#14-S8 권한이 없으면 403과 JSON 에러를 받는다")
	void accessDenied() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		responder.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
		assertThat(response.getContentAsString(StandardCharsets.UTF_8))
			.isEqualTo("{\"code\":\"FORBIDDEN\",\"message\":\"권한이 없습니다.\"}");
	}

}
