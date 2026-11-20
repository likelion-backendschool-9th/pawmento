package com.pawmento.diary.adapter.in.web;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.diary.adapter.in.web.exception.InvalidDiaryRequestException;
import com.pawmento.diary.application.port.in.DiaryLocationChange;
import com.pawmento.global.exception.RuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DiaryLocationRequestValuesTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  private DiaryLocationChange parse(String json) {
    return DiaryRequestValues.features(null, mapper.readTree(json)).location();
  }

  @Test
  @DisplayName("#44-S4 생략·NONE·MANUAL·AUTO를 코어 입력에 보존하고 수동 좌표를 버리지 않는다")
  void keepsSelection() {
    assertThat(DiaryRequestValues.features(null, null).location().kind())
        .isEqualTo(DiaryLocationChange.Kind.KEEP);
    assertThat(parse("{\"mode\":\"NONE\"}").kind()).isEqualTo(DiaryLocationChange.Kind.NONE);
    var manual =
        parse("{\"mode\":\"MANUAL\",\"latitude\":-0.0,\"longitude\":-73.5,\"placeName\":\" 공원 \"}");
    assertThat(manual.kind()).isEqualTo(DiaryLocationChange.Kind.MANUAL);
    assertThat(manual.intent().latitude()).isEqualTo(0.0);
    assertThat(manual.intent().longitude()).isEqualTo(-73.5);
    assertThat(manual.intent().placeName()).isEqualTo("공원");
    assertThat(manual.requiresMap()).isTrue();
    assertThat(parse("{\"mode\":\"AUTO\"}").requiresMap()).isTrue();
  }

  @Test
  @DisplayName("#44-S2 잘못된 위치 JSON·누락 좌표·문자열 숫자·범위 초과를 저장 전에 거부한다")
  void invalidInput() {
    for (String json :
        new String[] {
          "null",
          "[]",
          "{}",
          "{\"mode\":null}",
          "{\"mode\":\"OTHER\"}",
          "{\"mode\":\"MANUAL\"}",
          "{\"mode\":\"MANUAL\",\"latitude\":null,\"longitude\":127}",
          "{\"mode\":\"MANUAL\",\"latitude\":\"37\",\"longitude\":127}",
          "{\"mode\":\"MANUAL\",\"latitude\":true,\"longitude\":127}",
          "{\"mode\":\"MANUAL\",\"latitude\":91,\"longitude\":127}",
          "{\"mode\":\"MANUAL\",\"latitude\":0,\"longitude\":181}",
          "{\"mode\":\"MANUAL\",\"latitude\":0,\"longitude\":1e309}",
          "{\"mode\":\"MANUAL\",\"latitude\":0,\"longitude\":127,\"placeName\":3}"
        }) {
      assertThatThrownBy(() -> parse(json)).as(json).isInstanceOf(RuleViolationException.class);
    }
  }

  @Test
  @DisplayName("#44-S4 위치 객체의 서버 소유 필드·알 수 없는 필드와 NONE/AUTO 좌표 주입을 거부한다")
  void rejectsInjectedFields() {
    for (String field :
        new String[] {"revision", "source", "sourceAssetId", "createdBy", "extra"}) {
      String json = "{\"mode\":\"MANUAL\",\"latitude\":37,\"longitude\":127,\"" + field + "\":1}";
      assertThatThrownBy(() -> parse(json)).isInstanceOf(InvalidDiaryRequestException.class);
    }
    for (String mode : new String[] {"NONE", "AUTO"}) {
      assertThatThrownBy(() -> parse("{\"mode\":\"" + mode + "\",\"latitude\":0}"))
          .isInstanceOf(InvalidDiaryRequestException.class);
    }
  }

  @Test
  @DisplayName("#44-S7 장소명 null·공백은 이름 없음이고 사용자 255자 초과는 입력 오류다")
  void nameBoundary() {
    for (String suffix : new String[] {"", ",\"placeName\":null", ",\"placeName\":\" \""}) {
      assertThat(
              parse("{\"mode\":\"MANUAL\",\"latitude\":0,\"longitude\":0" + suffix + "}")
                  .intent()
                  .placeName())
          .isNull();
    }
    assertThatThrownBy(
            () ->
                parse(
                    "{\"mode\":\"MANUAL\",\"latitude\":0,\"longitude\":0,\"placeName\":\""
                        + "가".repeat(256)
                        + "\"}"))
        .isInstanceOf(RuleViolationException.class);
  }
}
