package com.pawmento.question.adapter.out.media;

import com.pawmento.media.application.port.in.*;
import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.QuestionMediaPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestionMediaAdapter implements QuestionMediaPort {
  private final ChangeMediaReferencesUseCase references;
  private final LockMediaReferencesUseCase locks;
  private final ValidateMediaReferencesUseCase validation;

  public void validate(Long member, Long pet, List<Long> previous, List<Long> requested) {
    validation.validate(
        new ValidateMediaReferencesCommand(
            member, pet, previous, requested, "QUESTION_ANSWER_ATTACHMENT"));
  }

  public void lock(Long member, Long pet, List<Long> ids) {
    locks.lock(new LockMediaReferencesCommand(member, pet, ids, "QUESTION_ANSWER_ATTACHMENT"));
  }

  private final LoadMediaAttachmentsUseCase attachments;
  private final GetAttachedMediaUseCase files;

  public void change(Long member, Long pet, List<Long> old, List<Long> next) {
    references.change(
        new ChangeMediaReferencesCommand(member, pet, old, next, "QUESTION_ANSWER_ATTACHMENT"));
  }

  public List<QuestionAttachment> metadata(Long pet, List<Long> ids) {
    return attachments
        .load(new LoadMediaAttachmentsQuery(pet, ids, "QUESTION_ANSWER_ATTACHMENT"))
        .stream()
        .map(
            m ->
                new QuestionAttachment(
                    m.assetId(),
                    m.type(),
                    m.mimeType(),
                    m.sizeBytes(),
                    m.durationMs(),
                    m.width(),
                    m.height(),
                    m.thumbnail(),
                    m.available(),
                    m.filename()))
        .toList();
  }

  public GetQuestionFileResult file(Long pet, Long id, boolean thumbnail) {
    var r = files.get(new GetAttachedMediaQuery(pet, id, thumbnail, "QUESTION_ANSWER_ATTACHMENT"));
    return new GetQuestionFileResult(r.filename(), r.mimeType(), r.sizeBytes(), r.content());
  }
}
