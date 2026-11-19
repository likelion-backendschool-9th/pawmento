package com.pawmento.diary.adapter.in.web;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.global.security.LoginMember;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    String content = DiaryRequestValues.text(request.content(), true);
    var date = DiaryRequestValues.date(request.recordedDate(), true);
    var features = DiaryRequestValues.features(request.attachmentIds(), request.location());
    return DiaryEntryResponse.from(
        create.create(new CreateDiaryEntryCommand(memberId, petId, content, date, features)));
  }

  @GetMapping("/{recordId}")
  DiaryEntryResponse get(
      @LoginMember Long memberId, HttpServletRequest http, @PathVariable Long recordId) {
    Long petId = DiaryQueryParameters.parse(http.getQueryString(), "petId").petId();
    DiaryRequestValues.id(recordId);
    return DiaryEntryResponse.from(get.get(new GetDiaryEntryQuery(memberId, petId, recordId)));
  }

  @PatchMapping("/{recordId}")
  DiaryEntryResponse edit(
      @LoginMember Long memberId,
      HttpServletRequest http,
      @PathVariable Long recordId,
      @RequestBody EditDiaryEntryRequest request) {
    Long petId = DiaryQueryParameters.parse(http.getQueryString(), "petId").petId();
    DiaryRequestValues.id(recordId);
    String content = DiaryRequestValues.text(request.content(), false);
    var date = DiaryRequestValues.date(request.recordedDate(), false);
    Long version = DiaryRequestValues.version(request.version());
    var features = DiaryRequestValues.features(request.attachmentIds(), request.location());
    return DiaryEntryResponse.from(
        edit.edit(
            new EditDiaryEntryCommand(
                memberId, petId, recordId, content, date, version, features)));
  }

  @DeleteMapping("/{recordId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@LoginMember Long memberId, @PathVariable Long recordId, HttpServletRequest http) {
    var query = DiaryQueryParameters.parse(http.getQueryString(), "petId", "version");
    Long petId = query.petId();
    DiaryRequestValues.id(recordId);
    deletion.delete(new DeleteDiaryEntryCommand(memberId, petId, recordId, query.version()));
  }
}
