package com.pawmento.diary.application.port.in;

import com.pawmento.diary.domain.DiaryLocationIntent;
import java.util.Objects;

/** JSON이 아닌 위치 변경 의도. 생략은 KEEP이고 명시적인 NONE과 구분한다. */
public record DiaryLocationChange(Kind kind, DiaryLocationIntent intent) {
  public enum Kind {
    KEEP,
    NONE,
    MANUAL,
    AUTO
  }

  public DiaryLocationChange {
    Objects.requireNonNull(kind, "위치 변경 종류가 필요합니다.");
    boolean matches =
        switch (kind) {
          case KEEP, AUTO -> intent == null;
          case NONE -> intent != null && intent.mode() == DiaryLocationIntent.Mode.NONE;
          case MANUAL -> intent != null && intent.mode() == DiaryLocationIntent.Mode.MANUAL;
        };
    if (!matches) throw new IllegalArgumentException("위치 변경 종류와 값이 일치하지 않습니다.");
  }

  public static DiaryLocationChange keep() {
    return new DiaryLocationChange(Kind.KEEP, null);
  }

  public static DiaryLocationChange none() {
    return new DiaryLocationChange(Kind.NONE, DiaryLocationIntent.none());
  }

  public static DiaryLocationChange manual(Double latitude, Double longitude, String name) {
    return new DiaryLocationChange(
        Kind.MANUAL, DiaryLocationIntent.manual(latitude, longitude, name));
  }

  public static DiaryLocationChange auto() {
    return new DiaryLocationChange(Kind.AUTO, null);
  }

  public boolean requiresMap() {
    return kind == Kind.MANUAL || kind == Kind.AUTO;
  }
}
