package com.pawmento.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// 공통 에러 처리가 값의 형식 오류를 우리 에러 형식으로 바꾸는지 확인한다. 특정 도메인에 기대지 않도록 시험용 컨트롤러를 쓴다
class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.setValidator(new LocalValidatorFactoryBean())
		.build();

	@Test
	@DisplayName("#19-S5 경로 변수를 숫자로 바꾸지 못하면 본문 없는 400이 아니라 JSON 에러를 받는다")
	void pathVariableTypeMismatch() throws Exception {
		mockMvc.perform(get("/items/abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("id 값의 형식이 올바르지 않습니다."));
	}

	@Test
	@DisplayName("#19-S5 쿼리 문자열 값을 숫자로 바꾸지 못하면 스프링 내부 메시지 대신 우리 문구를 받는다")
	void queryTypeMismatch() throws Exception {
		mockMvc.perform(get("/search?size=abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("size 값의 형식이 올바르지 않습니다."));
	}

	@Test
	@DisplayName("#19-S5 형식 오류가 아닌 검증 실패는 검증 문구를 그대로 받는다")
	void validationMessageIsKept() throws Exception {
		mockMvc.perform(get("/search"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("size를 입력해 주세요."));
	}

	@RestController
	static class ProbeController {

		@GetMapping("/items/{id}")
		String item(@PathVariable Long id) {
			return "ok";
		}

		@GetMapping("/search")
		String search(@Valid SearchRequest request) {
			return "ok";
		}

	}

	record SearchRequest(@NotNull(message = "size를 입력해 주세요.") Integer size) {
	}

}
