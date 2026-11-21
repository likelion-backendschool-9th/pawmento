package com.pawmento.postcard.application.service;

import com.pawmento.postcard.application.port.in.ProcessPostcardWorkUseCase;
import com.pawmento.postcard.application.port.out.*;
import com.pawmento.postcard.domain.Postcard;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 선점한 엽서는 서버가 재시작돼도 다시 부르지 않는다. 외부 쪽에서 이미 그렸을 수 있어 중복 과금이 된다.
// 파일과 DB는 완성 = 파일 저장 → DB 반영, 실패·삭제 = DB 반영 → 파일 삭제 순서다. 어긋난 파일은 정리 작업이 지운다.
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessPostcardWorkService implements ProcessPostcardWorkUseCase {
  private final PostcardWorkTransactions tx;
  private final PostcardWorkPort work;
  private final LoadPostcardPort load;
  private final GeneratePostcardImagePort generator;
  private final StorePostcardFilePort files;
  private final Clock clock;

  @Override
  public boolean processNext() {
    expire();
    var claimed = tx.claim(clock.instant());
    if (claimed.isEmpty()) return false;
    process(claimed.get());
    return true;
  }

  private void expire() {
    var cutoff = clock.instant().minus(Postcard.PROCESSING_EXPIRY);
    for (Long id : work.startedBefore(cutoff))
      load.findById(id).ifPresent(p -> {
        if (tx.fail(p)) log.warn("Postcard {} expired while processing", id);
      });
  }

  private void process(Postcard postcard) {
    Long id = postcard.getId();
    if (!postcard.generatedBy(generator.name())) {
      // 요청 때 동의한 외부 서비스가 아니면 보내지 않고 실패로 돌려준다.
      log.warn("Postcard {} was requested for another generator", id);
      finishFailed(postcard);
      return;
    }
    GeneratedPostcardImage image;
    String key;
    try {
      image = generator.generate(request(postcard));
      key = files.storeImage(id, image.bytes(), image.contentType());
    } catch (RuntimeException e) {
      // 외부 응답 본문이 섞일 수 있어 메시지는 남기지 않는다.
      log.warn("Postcard {} generation failed: {}", id, e.getClass().getSimpleName());
      finishFailed(postcard);
      return;
    }
    boolean completed;
    try {
      completed = tx.complete(id, key, image.contentType(), clock.instant());
    } catch (RuntimeException e) {
      files.deleteImage(key);
      throw e;
    }
    // 만드는 중 지웠거나 이미 만료됐다. 늦게 받은 그림은 남기지 않는다.
    if (!completed) files.deleteImage(key);
    deleteSource(postcard);
  }

  private PostcardImageRequest request(Postcard p) {
    byte[] photo = p.getSourcePhotoKey() == null ? null : files.readSourcePhoto(p.getSourcePhotoKey());
    return new PostcardImageRequest(
        p.getSourceContent(), p.getSourceNote(), p.getSourcePetSpecies(), p.getSourcePetBreed(), photo);
  }

  private void finishFailed(Postcard postcard) {
    tx.fail(postcard);
    deleteSource(postcard);
  }

  private void deleteSource(Postcard postcard) {
    if (postcard.getSourcePhotoKey() != null) files.deleteSourcePhoto(postcard.getSourcePhotoKey());
  }
}
