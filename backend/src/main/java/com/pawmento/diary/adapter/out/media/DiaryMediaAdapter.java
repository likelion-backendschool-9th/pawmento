package com.pawmento.diary.adapter.out.media;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.DiaryMediaPort;
import com.pawmento.media.application.port.in.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryMediaAdapter implements DiaryMediaPort {
  private final ChangeMediaReferencesUseCase references;
  private final LockMediaReferencesUseCase locks;

  public void lock(Long member, Long pet, List<Long> ids) {
    locks.lock(new LockMediaReferencesCommand(member, pet, ids));
  }

  private final LoadMediaAttachmentsUseCase attachments;
  private final GetAttachedMediaUseCase files;

  public void change(Long member, Long pet, List<Long> old, List<Long> next) {
    references.change(new ChangeMediaReferencesCommand(member, pet, old, next));
  }

  public List<DiaryAttachment> metadata(Long pet, List<Long> ids) {
    return attachments.load(new LoadMediaAttachmentsQuery(pet, ids)).stream()
        .map(
            m ->
                new DiaryAttachment(
                    m.assetId(),
                    m.type(),
                    m.mimeType(),
                    m.sizeBytes(),
                    m.durationMs(),
                    m.width(),
                    m.height(),
                    m.thumbnail(),
                    m.available()))
        .toList();
  }

  public GetDiaryFileResult file(Long pet, Long id, boolean thumbnail) {
    var r = files.get(new GetAttachedMediaQuery(pet, id, thumbnail));
    return new GetDiaryFileResult(r.filename(), r.mimeType(), r.sizeBytes(), r.content());
  }
}
