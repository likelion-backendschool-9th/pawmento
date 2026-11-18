package com.pawmento.community.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

// 앞뒤 공백을 지운 뒤 1~max자인지 본다. 필수 검사와 길이 검사를 한 어노테이션으로 해서
// 필드마다 오류가 하나만 나고, 공통 핸들러가 요청 필드 순서대로 첫 오류를 고른다.
// 공백 기준은 도메인(Post)과 같다: 프론트엔드의 trim()처럼 줄바꿈 없는 공백(U+00A0 등)과 BOM도 공백이다
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TextLength.Validator.class)
@Documented
@interface TextLength {

	int max();

	String message();

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<TextLength, String> {

		private int max;

		@Override
		public void initialize(TextLength constraint) {
			this.max = constraint.max();
		}

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			if (value == null) {
				return false;
			}
			int start = 0;
			int end = value.length();
			while (start < end && isSpace(value.charAt(start))) {
				start++;
			}
			while (end > start && isSpace(value.charAt(end - 1))) {
				end--;
			}
			int length = end - start;
			return length >= 1 && length <= max;
		}

		// Post의 공백 기준과 같다. 프론트엔드 trim()이 지우는 문자만 공백으로 본다(U+001C~U+001F는 남긴다)
		private static boolean isSpace(char c) {
			return (c >= '\t' && c <= '\r') || c == '\uFEFF' || Character.isSpaceChar(c);
		}

	}

}
