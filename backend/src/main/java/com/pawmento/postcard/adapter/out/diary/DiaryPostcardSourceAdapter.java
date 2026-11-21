package com.pawmento.postcard.adapter.out.diary;

import com.pawmento.diary.application.port.in.GetDiaryEntryQuery;
import com.pawmento.diary.application.port.in.GetDiaryEntryUseCase;
import com.pawmento.postcard.application.port.out.LoadPostcardSourcePort;
import com.pawmento.postcard.application.port.out.PostcardSource;
import com.pawmento.postcard.domain.PostcardSourceKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryPostcardSourceAdapter implements LoadPostcardSourcePort {
  private final GetDiaryEntryUseCase diaries;

  @Override
  public PostcardSource load(Long memberId, Long petId, PostcardSourceKind kind, Long sourceId) {
    var entry = diaries.get(new GetDiaryEntryQuery(memberId, petId, sourceId));
    // 첨부 순서에서 열 수 있는 첫 사진 한 장만 쓴다. 음성은 쓰지 않는다.
    Long photo =
        entry.attachments().stream()
            .filter(a -> "IMAGE".equals(a.type()) && a.available())
            .map(a -> a.assetId())
            .findFirst()
            .orElse(null);
    return new PostcardSource(entry.content(), photo);
  }
}
