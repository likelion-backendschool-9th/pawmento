package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.QuestionAnswer;
import com.pawmento.question.domain.exception.InvalidQuestionAnswerException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionAnswerAttachments {
  private final QuestionAttachmentsPort links;
  private final QuestionMediaPort media;

  public boolean changes(QuestionAnswer answer, List<Long> ids) {
    return ids != null && !ids.equals(links.find(answer.getId()));
  }

  public void replace(Long member, QuestionAnswer answer, List<Long> requested) {
    if (requested == null) return;
    var old = links.find(answer.getId());
    if (old.equals(requested)) return;
    var all = new TreeSet<Long>(old);
    all.addAll(requested);
    media.lock(member, answer.getPrompt().getPetId(), List.copyOf(all));
    media.validate(member, answer.getPrompt().getPetId(), old, requested);
    long size =
        media.metadata(answer.getPrompt().getPetId(), requested).stream()
            .mapToLong(QuestionAttachment::sizeBytes)
            .sum();
    if (size > 75L * 1024 * 1024) throw new InvalidQuestionAnswerException();
    links.replace(answer.getId(), requested);
    media.change(member, answer.getPrompt().getPetId(), old, requested);
  }

  public QuestionAnswerResult one(QuestionAnswer answer) {
    return many(answer.getPrompt().getPetId(), List.of(answer)).getFirst();
  }

  public List<QuestionAnswerResult> many(Long pet, List<QuestionAnswer> answers) {
    var linked = links.findAll(answers.stream().map(QuestionAnswer::getId).toList());
    var ids = linked.values().stream().flatMap(List::stream).distinct().toList();
    var metadata =
        ids.isEmpty()
            ? Map.<Long, QuestionAttachment>of()
            : media.metadata(pet, ids).stream()
                .collect(Collectors.toMap(QuestionAttachment::assetId, Function.identity()));
    return answers.stream()
        .map(
            a ->
                QuestionAnswerResult.from(
                    a,
                    linked.getOrDefault(a.getId(), List.of()).stream().map(metadata::get).toList()))
        .toList();
  }
}
