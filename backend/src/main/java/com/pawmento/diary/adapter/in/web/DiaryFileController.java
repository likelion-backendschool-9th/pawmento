package com.pawmento.diary.adapter.in.web;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.global.security.LoginMember;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/diaries")
@RequiredArgsConstructor
public class DiaryFileController {
  private final GetDiaryFileUseCase files;

  @GetMapping({"/{recordId}/attachments/{assetId}", "/{recordId}/attachments/{assetId}/thumbnail"})
  ResponseEntity<Resource> get(
      @LoginMember Long memberId,
      @PathVariable Long recordId,
      @PathVariable Long assetId,
      HttpServletRequest request) {
    var pet = DiaryQueryParameters.parse(request.getQueryString(), "petId").petId();
    DiaryRequestValues.id(recordId);
    DiaryRequestValues.id(assetId);
    var r =
        files.get(
            new GetDiaryFileQuery(
                memberId, pet, recordId, assetId, request.getRequestURI().endsWith("/thumbnail")));
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
    return response.body(new DiaryFileResource(r));
  }
}
