package com.pawmento.diary.application.port.out;

import com.pawmento.diary.application.port.in.*;
import java.util.List;

public interface DiaryMediaPort {
  void validate(Long memberId, Long petId, List<Long> previous, List<Long> requested);

  void lock(Long memberId, Long petId, List<Long> assetIds);

  void change(Long memberId, Long petId, List<Long> previous, List<Long> requested);

  List<DiaryAttachment> metadata(Long petId, List<Long> assetIds);

  GetDiaryFileResult file(Long petId, Long assetId, boolean thumbnail);
}
