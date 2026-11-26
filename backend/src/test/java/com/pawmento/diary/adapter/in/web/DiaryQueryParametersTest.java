package com.pawmento.diary.adapter.in.web;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.diary.adapter.in.web.exception.InvalidDiaryRequestException;
import org.junit.jupiter.api.*;

class DiaryQueryParametersTest {
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
