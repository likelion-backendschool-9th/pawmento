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
public class DiaryController {
  private final CreateDiaryEntryUseCase create;
  private final GetDiaryEntryUseCase get;
  private final EditDiaryEntryUseCase edit;
  private final DeleteDiaryEntryUseCase deletion;
  private final ListDiaryEntriesUseCase list;
  private final GetDiaryCalendarUseCase calendar;

  private final GetDiaryFileUseCase files;

  @GetMapping
  DiaryEntriesPageResponse list(@LoginMember Long memberId, HttpServletRequest http) {
    var query =
        DiaryQueryParameters.parse(
            http.getQueryString(), "petId", "date", "from", "to", "page", "size");
    return DiaryEntriesPageResponse.from(
        list.list(
            ListDiaryEntriesQuery.of(
                memberId,
                query.petId(),
                query.date("date"),
                query.date("from"),
                query.date("to"),
                query.integer("page", 0),
                query.integer("size", 20))));
  }

  @GetMapping("/calendar")
  DiaryCalendarResponse calendar(@LoginMember Long memberId, HttpServletRequest http) {
    var query = DiaryQueryParameters.parse(http.getQueryString(), "petId", "month");
    return DiaryCalendarResponse.from(
        calendar.get(new GetDiaryCalendarQuery(memberId, query.petId(), query.month())));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  DiaryEntryResponse create(
      @LoginMember Long memberId,
      HttpServletRequest http,
      @RequestBody CreateDiaryEntryRequest request) {
    Long petId = DiaryQueryParameters.parse(http.getQueryString(), "petId").petId();
    DiaryRequestValues.rejectAuthor(
        request.author(), request.authorNameSnapshot(), request.displayName());
    String content =
        request.content() == null ? "" : DiaryRequestValues.text(request.content(), true);
    var date = DiaryRequestValues.date(request.recordedDate(), true);
    var features = DiaryRequestValues.features(request.attachmentIds(), request.location());
    return DiaryEntryResponse.from(
        create.create(new CreateDiaryEntryCommand(memberId, petId, content, date, features)));
  }

  @GetMapping("/{diaryId}")
  DiaryEntryResponse get(
      @LoginMember Long memberId, HttpServletRequest http, @PathVariable Long diaryId) {
    Long petId = DiaryQueryParameters.parse(http.getQueryString(), "petId").petId();
    DiaryRequestValues.id(diaryId);
    return DiaryEntryResponse.from(get.get(new GetDiaryEntryQuery(memberId, petId, diaryId)));
  }

  @PatchMapping("/{diaryId}")
  DiaryEntryResponse edit(
      @LoginMember Long memberId,
      HttpServletRequest http,
      @PathVariable Long diaryId,
      @RequestBody EditDiaryEntryRequest request) {
    Long petId = DiaryQueryParameters.parse(http.getQueryString(), "petId").petId();
    DiaryRequestValues.id(diaryId);
    DiaryRequestValues.rejectAuthor(
        request.author(), request.authorNameSnapshot(), request.displayName());
    String content = DiaryRequestValues.text(request.content(), false);
    var date = DiaryRequestValues.date(request.recordedDate(), false);
    Long version = DiaryRequestValues.version(request.version());
    var features = DiaryRequestValues.features(request.attachmentIds(), request.location());
    return DiaryEntryResponse.from(
        edit.edit(
            new EditDiaryEntryCommand(memberId, petId, diaryId, content, date, version, features)));
  }

  @DeleteMapping("/{diaryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@LoginMember Long memberId, @PathVariable Long diaryId, HttpServletRequest http) {
    var query = DiaryQueryParameters.parse(http.getQueryString(), "petId", "version");
    Long petId = query.petId();
    DiaryRequestValues.id(diaryId);
    deletion.delete(new DeleteDiaryEntryCommand(memberId, petId, diaryId, query.version()));
  }

  @GetMapping({"/{diaryId}/attachments/{assetId}", "/{diaryId}/attachments/{assetId}/thumbnail"})
  ResponseEntity<Resource> file(
      @LoginMember Long memberId,
      @PathVariable Long diaryId,
      @PathVariable Long assetId,
      HttpServletRequest request) {
    var pet = DiaryQueryParameters.parse(request.getQueryString(), "petId").petId();
    DiaryRequestValues.id(diaryId);
    DiaryRequestValues.id(assetId);
    var r =
        files.get(
            new GetDiaryFileQuery(
                memberId, pet, diaryId, assetId, request.getRequestURI().endsWith("/thumbnail")));
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
