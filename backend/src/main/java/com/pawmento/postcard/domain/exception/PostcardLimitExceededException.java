package com.pawmento.postcard.domain.exception;

import com.pawmento.global.exception.StateConflictException;

// 다음 날이 되면 풀리므로 409로 둔다(계획의 가정).
public class PostcardLimitExceededException extends StateConflictException {
  private PostcardLimitExceededException(String message) {
    super(message);
  }

  public static PostcardLimitExceededException member(boolean beta, int limit) {
    return new PostcardLimitExceededException(
        (beta ? "엽서는 베타 기간에 하루 " : "엽서는 하루 ") + limit + "장까지 만들 수 있어요.");
  }

  public static PostcardLimitExceededException total() {
    return new PostcardLimitExceededException("오늘은 엽서를 더 만들 수 없어요. 내일 다시 시도해 주세요.");
  }
}
