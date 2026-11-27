package com.pawmento.postcard.application.service;

import com.pawmento.postcard.application.port.in.CleanupPostcardFilesUseCase;
import com.pawmento.postcard.application.port.out.*;
import com.pawmento.postcard.domain.PostcardStatus;
import java.time.Clock;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 정상 경로가 지우지 못한 파일(생성 중단, 삭제 실패, 요청 롤백)을 결국 지운다. AI 생성 재시도와는 별개다.
// images/는 COMPLETED이고 image_key가 같은 엽서의 파일만, sources/는 PROCESSING 엽서의 파일만 남긴다.
@Service
@RequiredArgsConstructor
public class CleanupPostcardFilesService implements CleanupPostcardFilesUseCase {
  private final CollectPostcardFilesPort files;
  private final LoadPostcardPort load;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public int cleanup() {
    var candidates = files.settledFiles(clock.instant());
    var postcards =
        load
            .findFiles(candidates.stream().map(PostcardFile::postcardId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(StoredPostcardFiles::postcardId, Function.identity()));
    int deleted = 0;
    for (var file : candidates) {
      if (keep(file, postcards.get(file.postcardId()))) continue;
      files.delete(file);
      deleted++;
    }
    return deleted;
  }

  static boolean keep(PostcardFile file, StoredPostcardFiles postcard) {
    if (file.partial() || postcard == null) return false;
    return switch (file.kind()) {
      case IMAGE ->
          postcard.status() == PostcardStatus.COMPLETED
              && file.name().equals(postcard.imageKey());
      case SOURCE ->
          postcard.status() == PostcardStatus.PROCESSING
              && file.name().equals(postcard.sourcePhotoKey());
    };
  }
}
