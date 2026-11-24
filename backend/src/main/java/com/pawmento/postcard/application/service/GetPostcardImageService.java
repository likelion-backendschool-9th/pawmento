package com.pawmento.postcard.application.service;

import com.pawmento.postcard.application.exception.PostcardNotFoundException;
import com.pawmento.postcard.application.port.in.*;
import com.pawmento.postcard.application.port.out.*;
import com.pawmento.postcard.domain.PostcardStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPostcardImageService implements GetPostcardImageUseCase {
  private final LoadPostcardPetPort pets;
  private final LoadPostcardPort load;
  private final StorePostcardFilePort files;

  @Override
  @Transactional(readOnly = true)
  public GetPostcardImageResult get(GetPostcardImageQuery q) {
    pets.loadOwned(q.memberId(), q.petId());
    var postcard = PostcardAccess.load(load, q.petId(), q.postcardId());
    // 완성되지 않은 엽서에는 이미지가 없다.
    if (postcard.getStatus() != PostcardStatus.COMPLETED) throw new PostcardNotFoundException();
    return new GetPostcardImageResult(
        postcard.getImageKey(), postcard.getImageContentType(), files.readImage(postcard.getImageKey()));
  }
}
