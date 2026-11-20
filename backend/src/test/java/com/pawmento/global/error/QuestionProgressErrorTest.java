package com.pawmento.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.question.application.exception.StoredQuestionInvalidException;
import com.pawmento.question.domain.*;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class QuestionProgressErrorTest {
  @Test
  @DisplayName("#31-S3 없는 회차·현재 진행 불일치는 공통 500 형식이고 내부 원인은 노출하지 않는다")
  void commonInternalErrorBody() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new Controller())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    for (String kind : new String[] {"missing", "inconsistent"})
      mvc.perform(get("/question-error/" + kind))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
          .andExpect(jsonPath("$.message").isString());
  }

  @RestController
  static class Controller {
    @GetMapping("/question-error/missing")
    public void missing() {
      throw new StoredQuestionInvalidException(
          new IllegalStateException("private internal details"));
    }

    @GetMapping("/question-error/inconsistent")
    public void inconsistent() {
      QuestionProgress.restore(1L, QuestionMode.ACTIVE, 10L, 2)
          .requireCurrent(
              QuestionPrompt.restore(10L, 2L, QuestionMode.ACTIVE, 1L, "질문", 1, Instant.EPOCH));
    }
  }
}
