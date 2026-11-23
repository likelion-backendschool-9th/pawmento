package com.pawmento.question.adapter.in.web;

import com.pawmento.question.application.port.in.*;
import com.pawmento.global.security.LoginMember;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class QuestionController {
  private final EnsureCurrentQuestionPromptUseCase ensure;
  private final GetCurrentQuestionPromptUseCase currentQuestions;

  private final CreateQuestionAnswerUseCase create;
  private final GetQuestionAnswerUseCase get;
  private final ListQuestionAnswersUseCase list;
  private final EditQuestionAnswerUseCase edit;
  private final DeleteQuestionAnswerUseCase delete;
  private final GetQuestionFileUseCase files;

  @GetMapping("/prompts/current")
  CurrentQuestionPromptResult current(@LoginMember Long memberId, HttpServletRequest request) {
    return currentQuestions.get(
        new GetCurrentQuestionPromptQuery(
            memberId, QuestionPetQuery.petId(request.getQueryString())));
  }

  @PostMapping("/prompts/current")
  ResponseEntity<CurrentQuestionPromptResult> ensure(
      @LoginMember Long memberId, HttpServletRequest request) {
    var result =
        ensure.ensure(
            new EnsureCurrentQuestionPromptCommand(
                memberId, QuestionPetQuery.petId(request.getQueryString())));
    return ResponseEntity.status(result.created() ? 201 : 200).body(result.current());
  }

  @PostMapping("/prompts/{promptId}/answers")
  ResponseEntity<QuestionAnswerResponse.Created> create(
      @LoginMember Long member,
      @PathVariable Long promptId,
      HttpServletRequest request,
      @RequestBody JsonNode body) {
    var values = QuestionAnswerBody.parse(body, false);
    var result =
        create.create(
            new CreateQuestionAnswerCommand(
                member,
                QuestionPetQuery.petId(request.getQueryString()),
                promptId,
                values.content(),
                values.attachmentIds()));
    return ResponseEntity.status(201).body(QuestionAnswerResponse.Created.from(result));
  }

  @GetMapping("/answers")
  QuestionAnswerResponse.Page list(@LoginMember Long member, HttpServletRequest request) {
    return QuestionAnswerResponse.Page.from(
        list.list(
            new QuestionAnswerQuery(
                    request.getQueryString(),
                    "petId",
                    "mode",
                    "promptId",
                    "authorMemberId",
                    "from",
                    "to",
                    "page",
                    "size")
                .list(member)));
  }

  @GetMapping("/answers/{answerId}")
  QuestionAnswerResponse get(
      @LoginMember Long member, @PathVariable Long answerId, HttpServletRequest request) {
    return QuestionAnswerResponse.from(
        get.get(
            new GetQuestionAnswerQuery(
                member, QuestionPetQuery.petId(request.getQueryString()), answerId)));
  }

  @PatchMapping("/answers/{answerId}")
  QuestionAnswerResponse edit(
      @LoginMember Long member,
      @PathVariable Long answerId,
      HttpServletRequest request,
      @RequestBody JsonNode body) {
    var values = QuestionAnswerBody.parse(body, true);
    return QuestionAnswerResponse.from(
        edit.edit(
            new EditQuestionAnswerCommand(
                member,
                QuestionPetQuery.petId(request.getQueryString()),
                answerId,
                values.content(),
                values.version(),
                values.attachmentIds())));
  }

  @DeleteMapping("/answers/{answerId}")
  ResponseEntity<Void> delete(
      @LoginMember Long member, @PathVariable Long answerId, HttpServletRequest request) {
    var query = new QuestionAnswerQuery(request.getQueryString(), "petId", "version");
    delete.delete(
        new DeleteQuestionAnswerCommand(member, query.petId(), answerId, query.version()));
    return ResponseEntity.noContent().build();
  }

  @GetMapping({
    "/answers/{answerId}/attachments/{assetId}/content",
    "/answers/{answerId}/attachments/{assetId}/thumbnail"
  })
  ResponseEntity<Resource> file(
      @LoginMember Long memberId,
      @PathVariable Long answerId,
      @PathVariable Long assetId,
      HttpServletRequest request) {
    var pet = QuestionPetQuery.petId(request.getQueryString());

    var r =
        files.get(
            new GetQuestionFileQuery(
                memberId, pet, answerId, assetId, request.getRequestURI().endsWith("/thumbnail")));
    var response =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(r.mimeType()))
            .header("X-Content-Type-Options", "nosniff")
            .header("Cache-Control", "private, no-store")
            .header("Accept-Ranges", "bytes")
            .header(
                "Content-Disposition",
                ContentDisposition.inline().filename(r.filename()).build().toString());
    if (request.getMethod().equals("HEAD")) return response.contentLength(r.sizeBytes()).build();
    return response.body(new QuestionFileResource(r));
  }
}
