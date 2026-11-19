package com.pawmento.diary.adapter.in.web;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.diary.adapter.in.web.exception.InvalidDiaryRequestException;
import org.junit.jupiter.api.*;

class DiaryQueryParametersTest {
  @Test
  @DisplayName("#32-S25 알 수 없는 키와 값의 인코딩 오류는 조회 조건에 영향을 주지 않는다")
  void ignoresUnknownParameters() {
    for (String extra : new String[] {"utm=%E0", "%E0=value", "utm=%", "utm=", "utm=1&utm=2"}) {
      assertThat(DiaryQueryParameters.parse("petId=1&" + extra, "petId").petId()).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("#32-S25 인코딩된 알려진 키도 중복·빈 값·잘못된 인코딩을 검사한다")
  void validatesEncodedKnownKeys() {
    assertThat(DiaryQueryParameters.parse("%70etId=2", "petId").petId()).isEqualTo(2);
    for (String query : new String[] {"petId=1&%70etId=2", "%70etId=", "%70etId=%E0", "petId=%"}) {
      assertThatThrownBy(() -> DiaryQueryParameters.parse(query, "petId"))
          .isInstanceOf(InvalidDiaryRequestException.class);
    }
  }
}
