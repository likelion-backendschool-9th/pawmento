package com.pawmento.diary.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawmento.diary.domain.exception.InvalidDiaryLocationIntentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiaryLocationIntentTest {
  @Test
  @DisplayName("#44-S2 수동 위치는 0도·남위·서경·허용 경계를 반올림 없이 보존한다")
  void preservesCoordinates() {
    for (double latitude : new double[] {-90, -37.1234567890123, 0, 37.5, 90}) {
      for (double longitude : new double[] {-180, -73.1234567890123, 0, 127.5, 180}) {
        var value = DiaryLocationIntent.manual(latitude, longitude, "공원");
        assertThat(value.mode()).isEqualTo(DiaryLocationIntent.Mode.MANUAL);
        assertThat(Double.doubleToLongBits(value.latitude()))
            .isEqualTo(Double.doubleToLongBits(latitude));
        assertThat(Double.doubleToLongBits(value.longitude()))
            .isEqualTo(Double.doubleToLongBits(longitude));
      }
    }
  }

  @Test
  @DisplayName("#44-S2 null·NaN·무한대·범위 초과 좌표는 수동 위치가 될 수 없다")
  void rejectsInvalidCoordinates() {
    for (Double latitude :
        new Double[] {
          null,
          Double.NaN,
          Double.POSITIVE_INFINITY,
          Double.NEGATIVE_INFINITY,
          Math.nextUp(90.0),
          Math.nextDown(-90.0)
        }) {
      assertThatThrownBy(() -> DiaryLocationIntent.manual(latitude, 127.0, null))
          .isInstanceOf(InvalidDiaryLocationIntentException.class);
    }
    for (Double longitude :
        new Double[] {
          null,
          Double.NaN,
          Double.POSITIVE_INFINITY,
          Double.NEGATIVE_INFINITY,
          Math.nextUp(180.0),
          Math.nextDown(-180.0)
        }) {
      assertThatThrownBy(() -> DiaryLocationIntent.manual(37.0, longitude, null))
          .isInstanceOf(InvalidDiaryLocationIntentException.class);
    }
  }

  @Test
  @DisplayName("#44-S4 NONE은 좌표·장소명을 보관하지 않고 부가 값을 받으면 거부한다")
  void noneHasNoLocation() {
    var value = DiaryLocationIntent.none();
    assertThat(value.mode()).isEqualTo(DiaryLocationIntent.Mode.NONE);
    assertThat(value.latitude()).isNull();
    assertThat(value.longitude()).isNull();
    assertThat(value.placeName()).isNull();
    assertThatThrownBy(() -> new DiaryLocationIntent(null, null, null, null))
        .isInstanceOf(InvalidDiaryLocationIntentException.class);
    assertThatThrownBy(
            () -> new DiaryLocationIntent(DiaryLocationIntent.Mode.NONE, 0.0, null, null))
        .isInstanceOf(InvalidDiaryLocationIntentException.class);
    assertThatThrownBy(
            () -> new DiaryLocationIntent(DiaryLocationIntent.Mode.NONE, null, 0.0, null))
        .isInstanceOf(InvalidDiaryLocationIntentException.class);
    assertThatThrownBy(
            () -> new DiaryLocationIntent(DiaryLocationIntent.Mode.NONE, null, null, " "))
        .isInstanceOf(InvalidDiaryLocationIntentException.class);
  }

  @Test
  @DisplayName("#44-S7 사용자 장소명은 strip 후 255 UTF-16 단위를 검사하며 임의로 자르지 않는다")
  void normalizesUserPlaceName() {
    for (String name : new String[] {null, "", " \t\n ", "\u2003"}) {
      assertThat(DiaryLocationIntent.manual(37.0, 127.0, name).placeName()).isNull();
    }
    assertThat(DiaryLocationIntent.manual(37.0, 127.0, " 공원 ").placeName()).isEqualTo("공원");
    String max = "가".repeat(255);
    assertThat(DiaryLocationIntent.manual(37.0, 127.0, " " + max + " ").placeName()).isEqualTo(max);
    for (String tooLong : new String[] {"가".repeat(256), "🐾".repeat(128)}) {
      assertThatThrownBy(() -> DiaryLocationIntent.manual(37.0, 127.0, tooLong))
          .isInstanceOf(InvalidDiaryLocationIntentException.class);
    }
  }

  @Test
  @DisplayName("#44-S13 좌표의 0 부호를 정규화하고 다른 유효 좌표에는 허용 오차를 적용하지 않는다")
  void normalizesOnlySignedZero() {
    var normalized = DiaryLocationIntent.manual(-0.0, -0.0, null);
    assertThat(normalized).isEqualTo(DiaryLocationIntent.manual(0.0, 0.0, null));
    assertThat(Double.doubleToLongBits(normalized.latitude())).isZero();
    assertThat(Double.doubleToLongBits(normalized.longitude())).isZero();
    assertThat(DiaryLocationIntent.manual(37.0, 127.0, null))
        .isNotEqualTo(DiaryLocationIntent.manual(Math.nextUp(37.0), 127.0, null));
  }
}
