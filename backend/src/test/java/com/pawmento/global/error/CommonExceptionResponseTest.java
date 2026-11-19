package com.pawmento.global.error;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.global.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class CommonExceptionResponseTest {
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new FixtureController())
          .setControllerAdvice(new GlobalExceptionHandler())
          .build();

  @Test
  @DisplayName("#33-S2 공통 기본 예외는 도메인 import 없이 400·403·404·409와 공통 본문으로 변환한다")
  void businessFailures() throws Exception {
    for (var row :
        new Object[][] {
          {"invalid", 400, "VALIDATION"},
          {"denied", 403, "FORBIDDEN"},
          {"missing", 404, "NOT_FOUND"},
          {"conflict", 409, "CONFLICT"},
          {"large", 413, "VALIDATION"},
          {"unsupported", 415, "VALIDATION"},
          {"storage", 503, "STORAGE_UNAVAILABLE"}
        }) {
      mvc.perform(get("/fixture/" + row[0]))
          .andExpect(status().is((Integer) row[1]))
          .andExpect(jsonPath("$.code").value(row[2]))
          .andExpect(jsonPath("$.message").value("safe message"));
    }
  }

  @Test
  @DisplayName("#72-S9 저장 데이터 오류는 원인(cause)이 400 예외여도 500 INTERNAL_ERROR이고 원인·메시지를 응답에 싣지 않는다")
  void storedDataInvalid() throws Exception {
    mvc.perform(get("/fixture/stored-invalid"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal row"))));
  }

  @Test
  @DisplayName("#33-S16 프로그래밍 오류 IllegalArgumentException을 사용자 입력 400으로 숨기지 않는다")
  void programmingError() {
    assertThatThrownBy(() -> mvc.perform(get("/fixture/programming")))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("#33-S15 multipart 파싱 오류도 전역 핸들러에서 공통 형식으로 응답한다")
  void multipartErrors() throws Exception {
    mvc.perform(get("/fixture/multipart-large"))
        .andExpect(status().is(413))
        .andExpect(jsonPath("$.code").value("VALIDATION"));
    mvc.perform(get("/fixture/multipart-invalid"))
        .andExpect(status().is(400))
        .andExpect(jsonPath("$.code").value("VALIDATION"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal parser details"))));
    mvc.perform(get("/fixture/storage"))
        .andExpect(status().is(503))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal storage path"))));
  }

  @RestController
  static class FixtureController {
    @GetMapping("/fixture/{kind}")
    void fail(@PathVariable String kind) {
      switch (kind) {
        case "invalid" -> throw new RuleViolationException("safe message") {};
        case "denied" -> throw new PermissionDeniedException("safe message") {};
        case "missing" -> throw new ResourceNotFoundException("safe message") {};
        case "conflict" -> throw new StateConflictException("safe message") {};
        case "large" -> throw new ContentLimitExceededException("safe message") {};
        case "unsupported" -> throw new UnsupportedFormatException("safe message") {};
        case "storage" ->
            throw new StorageUnavailableException(
                "safe message", new java.io.IOException("internal storage path")) {};
        case "stored-invalid" ->
            throw new StoredDataInvalidException(
                "internal row 5", new RuleViolationException("cause is a 400 exception") {}) {};
        case "multipart-large" ->
            throw new org.springframework.web.multipart.MaxUploadSizeExceededException(15728640);
        case "multipart-invalid" ->
            throw new org.springframework.web.multipart.MultipartException(
                "internal parser details");
        default -> throw new IllegalArgumentException("internal details");
      }
    }
  }
}
