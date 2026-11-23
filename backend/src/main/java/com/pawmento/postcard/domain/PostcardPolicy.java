package com.pawmento.postcard.domain;

import com.pawmento.postcard.domain.exception.InvalidPostcardException;
import com.pawmento.postcard.domain.exception.PostcardLimitExceededException;

// 베타 여부와 하루 제한. 설정으로 바꾸며, 전체 상한은 키 유출·버그로 요청이 몰릴 때 비용을 막는다.
public record PostcardPolicy(boolean beta, int dailyLimitPerMember, int dailyLimitTotal) {
  public PostcardPolicy {
    if (dailyLimitPerMember < 0 || dailyLimitTotal < 0)
      throw new InvalidPostcardException("엽서 하루 제한은 0 이상이어야 해요.");
  }

  // 전체 상한을 먼저 본다. 둘 다 넘으면 내일 다시 하라는 안내가 맞다.
  public void requireRoom(long memberUsed, long totalUsed) {
    if (totalUsed >= dailyLimitTotal) throw PostcardLimitExceededException.total();
    if (memberUsed >= dailyLimitPerMember)
      throw PostcardLimitExceededException.member(beta, dailyLimitPerMember);
  }

  public long remaining(long memberUsed) {
    return Math.max(0, dailyLimitPerMember - memberUsed);
  }
}
