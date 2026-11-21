package com.pawmento.postcard.application.service;

import com.pawmento.postcard.application.port.in.*;
import com.pawmento.postcard.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// DB 삭제를 먼저 커밋하고 파일을 지운다. 파일 삭제가 실패해도 정리 작업이 지운다.
// 만드는 중인 엽서도 지운다. 작업이 끝나면 받은 그림을 저장하지 않는다. 횟수는 돌려주지 않는다.
@Service
@RequiredArgsConstructor
public class DeletePostcardService implements DeletePostcardUseCase {
  private final LoadPostcardPetPort pets;
  private final LoadPostcardPort load;
  private final DeletePostcardPort delete;
  private final StorePostcardFilePort files;

  @Override
  public void delete(DeletePostcardCommand c) {
    pets.loadOwned(c.memberId(), c.petId());
    var postcard = PostcardAccess.load(load, c.petId(), c.postcardId());
    if (!delete.deleteById(postcard.getId())) return;
    if (postcard.getImageKey() != null) files.deleteImage(postcard.getImageKey());
    if (postcard.getSourcePhotoKey() != null) files.deleteSourcePhoto(postcard.getSourcePhotoKey());
  }
}
