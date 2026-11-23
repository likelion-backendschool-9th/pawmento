package com.pawmento.postcard.adapter.out.diary;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.postcard.application.exception.PostcardStorageUnavailableException;
import java.io.IOException;
import com.pawmento.postcard.application.port.out.LoadPostcardSourcePort;
import com.pawmento.postcard.application.port.out.PostcardSource;
import com.pawmento.postcard.domain.PostcardSourceKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryPostcardSourceAdapter implements LoadPostcardSourcePort {
  static final int MAX_THUMBNAIL = 1024 * 1024;

  private final GetDiaryEntryUseCase diaries;
  private final GetDiaryFileUseCase files;

  @Override
  public PostcardSource load(Long memberId, Long petId, PostcardSourceKind kind, Long sourceId) {
    var entry = diaries.get(new GetDiaryEntryQuery(memberId, petId, sourceId));
    // 첨부 순서에서 열 수 있는(썸네일이 있는) 첫 사진 한 장만 쓴다. 음성은 쓰지 않는다.
    Long photo =
        entry.attachments().stream()
            .filter(a -> "IMAGE".equals(a.type()) && a.available() && a.thumbnail())
            .map(a -> a.assetId())
            .findFirst()
            .orElse(null);
    return new PostcardSource(entry.content(), photo);
  }

  // 원본 대신 media 썸네일(최대 512px JPEG, EXIF 제거)을 보낸다.
  @Override
  public byte[] photo(
      Long memberId, Long petId, PostcardSourceKind kind, Long sourceId, Long assetId) {
    var file = files.get(new GetDiaryFileQuery(memberId, petId, sourceId, assetId, true));
    try (var in = file.content().get()) {
      byte[] bytes = in.readNBytes(MAX_THUMBNAIL + 1);
      if (bytes.length > MAX_THUMBNAIL) throw new PostcardStorageUnavailableException(null);
      return bytes;
    } catch (IOException e) {
      throw new PostcardStorageUnavailableException(e);
    }
  }
}
