package com.pawmento.diary.adapter.out.map;

import com.pawmento.diary.application.port.out.DiaryMapPort;
import com.pawmento.diary.application.port.out.DiaryMapSnapshot;
import com.pawmento.diary.domain.DiaryLocationIntent;
import com.pawmento.map.application.port.in.ApplyDiaryLocationCommand;
import com.pawmento.map.application.port.in.ApplyDiaryLocationUseCase;
import com.pawmento.map.application.port.in.GetDiaryLocationUseCase;
import com.pawmento.map.application.port.in.ReconcileDiaryLocationCommand;
import com.pawmento.map.application.port.in.ReconcileDiaryLocationUseCase;
import com.pawmento.map.application.port.in.ResolveLocationAddressUseCase;
import com.pawmento.map.application.port.in.DiaryLocationSnapshot;
import com.pawmento.map.application.port.in.DiaryLocationInput;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryMapAdapter implements DiaryMapPort {
  private final GetDiaryLocationUseCase read;
  private final ApplyDiaryLocationUseCase apply;
  private final ReconcileDiaryLocationUseCase reconcile;
  private final ResolveLocationAddressUseCase address;

  public Optional<DiaryMapSnapshot> find(Long id) {
    return read.get(id)
        .map(
            s ->
                new DiaryMapSnapshot(
                    s.rowId(),
                    s.diaryId(),
                    s.petId(),
                    s.createdBy(),
                    s.latitude(),
                    s.longitude(),
                    s.placeName(),
                    s.source() == null ? null : DiaryMapSnapshot.Source.valueOf(s.source().name()),
                    s.sourceAssetId(),
                    s.revision()));
  }

  public Optional<String> resolveAddress(double lat, double lon) {
    return address.resolve(lat, lon);
  }

  public Result apply(Long id, Long pet, Long author, long revision, DiaryLocationIntent intent) {
    return Result.valueOf(
        apply
            .apply(new ApplyDiaryLocationCommand(id, pet, author, revision, value(intent)))
            .name());
  }

  public Result reconcile(
      Long id,
      Long pet,
      Long author,
      long revision,
      DiaryMapSnapshot expected,
      DiaryLocationIntent intent) {
    var state =
        expected == null
            ? null
            : new DiaryLocationSnapshot(
                expected.rowId(),
                expected.diaryId(),
                expected.petId(),
                expected.createdBy(),
                expected.latitude(),
                expected.longitude(),
                expected.placeName(),
                expected.source() == null ? null : DiaryLocationSnapshot.Source.valueOf(expected.source().name()),
                expected.sourceAssetId(),
                expected.revision());
    return Result.valueOf(
        reconcile
            .reconcile(
                new ReconcileDiaryLocationCommand(id, pet, author, revision, state, value(intent)))
            .name());
  }

  private static DiaryLocationInput value(DiaryLocationIntent v) {
    return v == null || v.mode() == DiaryLocationIntent.Mode.NONE
        ? null
        : new DiaryLocationInput(v.latitude(), v.longitude(), v.placeName());
  }
}
