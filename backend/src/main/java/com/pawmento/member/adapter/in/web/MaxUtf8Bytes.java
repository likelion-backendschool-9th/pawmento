package com.pawmento.member.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

// 문자열의 UTF-8 바이트 수 제한. 글자 수(@Size)로는 한글처럼 한 글자가 여러 바이트인 값을 막지 못한다
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
@Documented
public @interface MaxUtf8Bytes {

	int value();

	String message() default "값이 너무 깁니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {

		private int max;

		@Override
		public void initialize(MaxUtf8Bytes constraint) {
			this.max = constraint.value();
		}

		// 값이 없는 경우는 @NotBlank가 맡는다
		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
		}

	}

}
