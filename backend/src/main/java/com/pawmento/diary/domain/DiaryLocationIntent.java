package com.pawmento.diary.domain;

import com.pawmento.diary.domain.exception.InvalidDiaryLocationIntentException;

/**
 * 일기가 보관할 위치 의도. 지도 핀 엔티티와 독립적이며 이번 단계는 수동 위치와 해제만 다룬다. 요청에서 location을 생략한 상태는 이 값의 NONE과 다르며 호출
 * 계층에서 구분한다.
 */
public record DiaryLocationIntent(Mode mode, Double latitude, Double longitude, String placeName) {
  public enum Mode {
    NONE,
    MANUAL
  }

  public DiaryLocationIntent {
    if (mode == null) throw new InvalidDiaryLocationIntentException("위치 모드를 확인해 주세요.");
    if (mode == Mode.NONE) {
      if (latitude != null || longitude != null || placeName != null)
        throw new InvalidDiaryLocationIntentException("위치 해제에는 좌표와 장소명을 지정할 수 없어요.");
    } else {
      requireCoordinate(latitude, 90, "위도");
      requireCoordinate(longitude, 180, "경도");
      // PostgreSQL double precision에 그대로 전달한다. 소수 반올림 없이 0의 부호만 통일한다.
      latitude = latitude == 0.0 ? 0.0 : latitude;
      longitude = longitude == 0.0 ? 0.0 : longitude;
      placeName = placeName == null ? null : placeName.strip();
      if (placeName != null && placeName.isEmpty()) placeName = null;
      if (placeName != null && placeName.length() > 255)
        throw new InvalidDiaryLocationIntentException("장소 이름은 255자 이하여야 해요.");
    }
  }

  public static DiaryLocationIntent none() {
    return new DiaryLocationIntent(Mode.NONE, null, null, null);
  }

  public static DiaryLocationIntent manual(Double latitude, Double longitude, String placeName) {
    return new DiaryLocationIntent(Mode.MANUAL, latitude, longitude, placeName);
  }

  private static void requireCoordinate(Double coordinate, int maximum, String name) {
    if (coordinate == null || !Double.isFinite(coordinate) || Math.abs(coordinate) > maximum)
      throw new InvalidDiaryLocationIntentException(name + " 값을 확인해 주세요.");
  }
}
