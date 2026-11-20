package com.pawmento.question.adapter.in.web;

import com.pawmento.global.security.LoginMember;
import com.pawmento.question.application.port.in.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class QuestionFileController {
  private final GetQuestionFileUseCase files;

  @GetMapping({
    "/answers/{answerId}/attachments/{assetId}/content",
    "/answers/{answerId}/attachments/{assetId}/thumbnail"
  })
  ResponseEntity<Resource> get(
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
