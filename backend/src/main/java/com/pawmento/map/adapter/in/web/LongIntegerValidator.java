package com.pawmento.map.adapter.in.web;

import java.math.BigDecimal;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// @Digits는 지수가 매우 큰 값(1e2147483647)에서 자릿수 계산이 넘쳐 통과시키고, 19자리 Long 값은 거절한다.
// 자릿수를 세지 않고 "소수점 아래 자릿수가 없는가"와 "Long 범위인가"를 직접 비교한다
class LongIntegerValidator implements ConstraintValidator<LongInteger, BigDecimal> {

	private static final BigDecimal MIN = BigDecimal.valueOf(Long.MIN_VALUE);
	private static final BigDecimal MAX = BigDecimal.valueOf(Long.MAX_VALUE);

	@Override
	public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		// 소수점 아래 자릿수가 있으면(scale > 0) 4.0처럼 값이 정수여도 거절한다. 4e3(scale -3)은 소수가 아니다
		if (value.scale() > 0) {
			return false;
		}
		return value.compareTo(MIN) >= 0 && value.compareTo(MAX) <= 0;
	}

}
