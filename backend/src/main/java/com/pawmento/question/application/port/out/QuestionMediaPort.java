package com.pawmento.question.application.port.out;

import com.pawmento.question.application.port.in.*;
import java.util.List;

public interface QuestionMediaPort {
  /** previous는 부모 권한 확인 후 DB에서 읽은 실제 연결이며 requested와 구분한다. */
  void lock(Long member, Long pet, List<Long> previous, List<Long> requested);

  void validate(Long member, Long pet, List<Long> old, List<Long> next);

  void change(Long member, Long pet, List<Long> old, List<Long> next);

  List<QuestionAttachment> metadata(Long pet, List<Long> ids);

  GetQuestionFileResult file(Long pet, Long asset, boolean thumbnail);
}
