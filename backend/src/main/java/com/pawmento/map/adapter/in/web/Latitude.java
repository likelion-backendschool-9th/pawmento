package com.pawmento.map.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

// 위도 범위(-90 ~ 90). 값이 없는지는 @NotNull이 따로 본다
@DecimalMin("-90.0")
@DecimalMax("90.0")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Documented
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@interface Latitude {

	String message() default "위도는 -90 이상 90 이하로 입력해 주세요.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
