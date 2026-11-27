package com.pawmento.diary.application.port.out;

import com.pawmento.diary.application.port.in.*;
import java.util.List;

public interface DiaryMediaPort {
  void validate(Long memberId, Long petId, List<Long> previous, List<Long> requested);

  /** previous는 부모 권한 확인 후 DB에서 읽은 실제 연결이며 requested와 구분한다. */
  void lock(Long memberId, Long petId, List<Long> previous, List<Long> requested);

  void change(Long memberId, Long petId, List<Long> previous, List<Long> requested);

  List<DiaryAttachment> metadata(Long petId, List<Long> assetIds);

  GetDiaryFileResult file(Long petId, Long assetId, boolean thumbnail);
}
