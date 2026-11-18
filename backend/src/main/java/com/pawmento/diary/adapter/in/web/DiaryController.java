package com.pawmento.diary.adapter.in.web;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.global.security.LoginMember;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pets/{petId}/diaries")
@RequiredArgsConstructor
public class DiaryController {
  private final CreateDiaryEntryUseCase create;
  private final GetDiaryEntryUseCase get;
  private final EditDiaryEntryUseCase edit;
  private final DeleteDiaryEntryUseCase deletion;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  DiaryEntryResponse create(
      @LoginMember Long memberId,
      @PathVariable Long petId,
      @RequestBody CreateDiaryEntryRequest request) {
    DiaryRequestValues.id(petId);
    String content = DiaryRequestValues.text(request.content(), true);
    var date = DiaryRequestValues.date(request.recordedDate(), true);
    DiaryRequestValues.unsupported(request.attachmentIds(), request.location());
    return DiaryEntryResponse.from(
        create.create(new CreateDiaryEntryCommand(memberId, petId, content, date)));
  }

  @GetMapping("/{recordId}")
  DiaryEntryResponse get(
      @LoginMember Long memberId, @PathVariable Long petId, @PathVariable Long recordId) {
    DiaryRequestValues.id(petId);
    DiaryRequestValues.id(recordId);
    return DiaryEntryResponse.from(get.get(new GetDiaryEntryQuery(memberId, petId, recordId)));
  }

  @PatchMapping("/{recordId}")
  DiaryEntryResponse edit(
      @LoginMember Long memberId,
      @PathVariable Long petId,
      @PathVariable Long recordId,
      @RequestBody EditDiaryEntryRequest request) {
    DiaryRequestValues.id(petId);
    DiaryRequestValues.id(recordId);
    String content = DiaryRequestValues.text(request.content(), false);
    var date = DiaryRequestValues.date(request.recordedDate(), false);
    Long version = DiaryRequestValues.version(request.version());
    DiaryRequestValues.unsupported(request.attachmentIds(), request.location());
    return DiaryEntryResponse.from(
        edit.edit(new EditDiaryEntryCommand(memberId, petId, recordId, content, date, version)));
  }

  @DeleteMapping("/{recordId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(
      @LoginMember Long memberId,
      @PathVariable Long petId,
      @PathVariable Long recordId,
      HttpServletRequest request) {
    DiaryRequestValues.id(petId);
    DiaryRequestValues.id(recordId);
    deletion.delete(
        new DeleteDiaryEntryCommand(
            memberId, petId, recordId, DiaryRequestValues.deleteVersion(request.getQueryString())));
  }
}
