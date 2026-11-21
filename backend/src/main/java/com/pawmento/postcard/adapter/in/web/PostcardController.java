package com.pawmento.postcard.adapter.in.web;

import com.pawmento.global.security.LoginMember;
import com.pawmento.postcard.application.port.in.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/postcards")
@RequiredArgsConstructor
public class PostcardController {
  private final RequestPostcardUseCase request;
  private final GetPostcardUseCase get;

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
                memberId, petId, body.source().kind(), body.source().id(), body.note())));
  }

  @GetMapping("/{postcardId}")
  PostcardResponse get(
      @LoginMember Long memberId, HttpServletRequest http, @PathVariable Long postcardId) {
    Long petId = PostcardQueryParameters.parse(http.getQueryString(), "petId").petId();
    return PostcardResponse.from(get.get(new GetPostcardQuery(memberId, petId, postcardId)));
  }
}
