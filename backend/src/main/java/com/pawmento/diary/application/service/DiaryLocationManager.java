package com.pawmento.diary.application.service;

import com.pawmento.diary.application.exception.*;
import com.pawmento.diary.application.port.out.*;
import com.pawmento.diary.domain.*;
import com.pawmento.diary.domain.exception.InvalidDiaryLocationIntentException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiaryLocationManager {
  private final DiaryMapPort map;

  record Observation(DiaryMapSnapshot state, boolean legacy, boolean coherent) {}

  Observation observe(DiaryEntry entry) {
    var state = map.find(entry.getId()).orElse(null);
    boolean legacy = entry.getLocationIntent() == null;
    if (legacy) {
      if (state != null
          && (!Objects.equals(state.petId(), entry.getPetId())
              || !Objects.equals(state.createdBy(), entry.getCreatedBy())
              || !"MANUAL".equals(state.source())
              || state.sourceAssetId() != null
              || state.revision() < 0)) throw invalid();
      entry.initializeLocationIntent(state == null ? DiaryLocationIntent.none() : intent(state));
    }
    return new Observation(state, legacy, legacy || matches(entry, state));
  }

  void requireCoherent(DiaryEntry entry, Observation observed) {
    if (!observed.coherent()) {
      log.error("일기 위치 원본과 핀이 다릅니다. diaryId={}, operation=KEEP", entry.getId());
      throw invalid();
    }
  }

  void synchronize(DiaryEntry saved, Observation observed, boolean explicit) {
    if (!observed.coherent())
      log.error("불일치 위치를 원본으로 정정합니다. diaryId={}, operation=REPLACE", saved.getId());
    var result =
        explicit || observed.legacy()
            ? map.reconcile(
                saved.getId(),
                saved.getPetId(),
                saved.getCreatedBy(),
                saved.getVersion(),
                observed.state(),
                saved.getLocationIntent())
            : map.apply(
                saved.getId(),
                saved.getPetId(),
                saved.getCreatedBy(),
                saved.getVersion(),
                saved.getLocationIntent());
    check(result);
  }

  void created(DiaryEntry saved) {
    if (map.find(saved.getId()).isPresent()) throw invalid();
    check(
        map.apply(
            saved.getId(),
            saved.getPetId(),
            saved.getCreatedBy(),
            saved.getVersion(),
            saved.getLocationIntent()));
  }

  void deleted(DiaryEntry entry, Observation observed) {
    if (!observed.coherent())
      log.error("불일치 위치를 포함해 일기를 삭제합니다. diaryId={}, operation=DELETE", entry.getId());
    check(
        map.reconcile(
            entry.getId(),
            entry.getPetId(),
            entry.getCreatedBy(),
            next(entry.getVersion()),
            observed.state(),
            null));
  }

  static long next(long version) {
    try {
      return Math.addExact(version, 1);
    } catch (ArithmeticException e) {
      throw new DiaryLocationDataInvalidException("일기 버전 범위를 초과했습니다.", e);
    }
  }

  private static void check(DiaryMapPort.Result result) {
    if (result == DiaryMapPort.Result.STALE) throw new DiaryVersionConflictException();
  }

  private static boolean matches(DiaryEntry e, DiaryMapSnapshot s) {
    var original = e.getLocationIntent();
    if (original.mode() == DiaryLocationIntent.Mode.NONE) return s == null;
    if (s == null
        || !Objects.equals(s.petId(), e.getPetId())
        || !Objects.equals(s.createdBy(), e.getCreatedBy())
        || !"MANUAL".equals(s.source())
        || s.sourceAssetId() != null
        || s.revision() != e.getVersion()) return false;
    try {
      return original.equals(intent(s));
    } catch (DiaryLocationDataInvalidException ignored) {
      return false;
    }
  }

  private static DiaryLocationIntent intent(DiaryMapSnapshot s) {
    try {
      return DiaryLocationIntent.manual(s.latitude(), s.longitude(), s.placeName());
    } catch (InvalidDiaryLocationIntentException e) {
      throw new DiaryLocationDataInvalidException("저장된 위치가 올바르지 않습니다.", e);
    }
  }

  private static DiaryLocationDataInvalidException invalid() {
    return new DiaryLocationDataInvalidException("일기 위치 원본과 지도 상태를 확인해야 합니다.");
  }
}
