package com.pawmento.postcard.adapter.in.web;

import com.pawmento.global.security.LoginMember;
import com.pawmento.postcard.application.port.in.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/postcards")
@RequiredArgsConstructor
public class PostcardController {
  private final RequestPostcardUseCase request;
  private final GetPostcardUseCase get;
  private final GetPostcardUsageUseCase usage;
  private final ListPostcardsUseCase list;
  private final GetPostcardImageUseCase images;
  private final DeletePostcardUseCase deletion;

  @GetMapping
  PostcardResponse.Page list(@LoginMember Long memberId, HttpServletRequest http) {
    var query = PostcardQueryParameters.parse(http.getQueryString(), "petId", "size", "cursor");
    return PostcardResponse.Page.from(
        list.list(
            new ListPostcardsQuery(
                memberId, query.petId(), query.size(20, 50), query.id("cursor"))));
  }

  // 생성은 기다리지 않는다. 상태는 상세를 다시 불러 확인한다.
  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  PostcardResponse.Created request(
      @LoginMember Long memberId,
      HttpServletRequest http,
      @Valid @RequestBody RequestPostcardRequest body) {
    Long petId = PostcardQueryParameters.parse(http.getQueryString(), "petId").petId();
    return PostcardResponse.Created.from(
        request.request(
            new RequestPostcardCommand(
                memberId,
                petId,
                body.source().kind(),
                body.source().id(),
                body.note(),
                Boolean.TRUE.equals(body.consent()))));
  }

  // 우체통 화면이 베타 표시와 오늘 남은 횟수를 그린다.
  @GetMapping("/usage")
  PostcardResponse.Usage usage(@LoginMember Long memberId, HttpServletRequest http) {
    Long petId = PostcardQueryParameters.parse(http.getQueryString(), "petId").petId();
    return PostcardResponse.Usage.from(usage.get(new GetPostcardUsageQuery(memberId, petId)));
  }

  @GetMapping("/{postcardId}")
  PostcardResponse get(
      @LoginMember Long memberId, HttpServletRequest http, @PathVariable Long postcardId) {
    Long petId = PostcardQueryParameters.parse(http.getQueryString(), "petId").petId();
    return PostcardResponse.from(get.get(new GetPostcardQuery(memberId, petId, postcardId)));
  }

  @GetMapping("/{postcardId}/image")
  ResponseEntity<byte[]> image(
      @LoginMember Long memberId, HttpServletRequest http, @PathVariable Long postcardId) {
    Long petId = PostcardQueryParameters.parse(http.getQueryString(), "petId").petId();
    var r = images.get(new GetPostcardImageQuery(memberId, petId, postcardId));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(r.contentType()))
        .header("X-Content-Type-Options", "nosniff")
        .header("Cache-Control", "private, no-store")
        .header(
            "Content-Disposition", ContentDisposition.inline().filename(r.filename()).build().toString())
        .body(r.bytes());
  }

  // 상태와 상관없이 지운다. 횟수는 돌려주지 않는다.
  @DeleteMapping("/{postcardId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@LoginMember Long memberId, HttpServletRequest http, @PathVariable Long postcardId) {
    Long petId = PostcardQueryParameters.parse(http.getQueryString(), "petId").petId();
    deletion.delete(new DeletePostcardCommand(memberId, petId, postcardId));
  }
}
