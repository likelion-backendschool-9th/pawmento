package com.pawmento.global.error;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawmento.map.application.port.in.ApplyDiaryLocationCommand;
import com.pawmento.map.application.port.in.DiaryLocationInput;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;
import com.pawmento.map.application.service.ApplyDiaryLocationService;
import com.pawmento.map.application.service.GetDiaryLocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class DiaryLocationContractErrorTest {
  @Test
  @DisplayName("#44-S6 내부 계약 오류는 MVC의 400 변환 없이 컨테이너 서버 오류로 전파된다")
  void internalContractErrorsEscapeValidationHandler() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new ContractController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    for (String path : new String[] {"id", "value"}) {
      assertThatThrownBy(() -> mvc.perform(get("/contract/" + path)))
          .isInstanceOf(jakarta.servlet.ServletException.class)
          .hasCauseInstanceOf(com.pawmento.map.application.exception.DiaryLocationContractViolation.class);
    }
  }

  @RestController
  static class ContractController {
    @GetMapping("/contract/id")
    public void invalidId() {
      new GetDiaryLocationService(mock(DiaryLocationStorePort.class)).get(0L);
    }

    @GetMapping("/contract/value")
    public void invalidValue() {
      new ApplyDiaryLocationService(mock(DiaryLocationStorePort.class))
          .apply(
              new ApplyDiaryLocationCommand(1L, 2L, 3L, 0, new DiaryLocationInput(91, 127, null)));
    }
  }
}
