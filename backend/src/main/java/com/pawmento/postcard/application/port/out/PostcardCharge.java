package com.pawmento.postcard.application.port.out;

import java.time.LocalDate;

// 실패로 돌린 엽서의 횟수를 돌려줄 회원과 날짜. 저장된 행이 복원되지 않아도 이 값으로 환불한다.
public record PostcardCharge(Long memberId, LocalDate requestedOn) {}
