package com.pawmento.community.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;
import com.pawmento.community.domain.exception.InvalidPostException;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

// 공백 판단이 요청 검증(TextLength)과 도메인(Post) 두 곳에 있다. 웹 어댑터가 도메인 클래스를 가져올 수 없어서다.
// 한쪽만 고쳐 어긋나지 않게, 모든 문자에서 두 판단이 같은지 확인한다
class TextLengthTest {

	private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
	private static final Validator VALIDATOR = FACTORY.getValidator();
	private static final PostCategory CARE = PostCategory.restore(2L, "CARE", "건강·돌봄", 2, true);

	@AfterAll
	static void close() {
		FACTORY.close();
	}

	@Test
	@DisplayName("#21-S2 BOM은 공백이고 U+001C~U+001F는 공백이 아니다")
	void bomIsSpaceButSeparatorsAreNot() {
		assertThat(titleAccepted("﻿")).isFalse();
		assertThat(titleAccepted(" ")).isFalse();
		assertThat(titleAccepted("\u001C")).isTrue();
		assertThat(titleAccepted("\u001F")).isTrue();
	}

	@Test
	@DisplayName("#21-S2 한 글자 제목을 받는지가 모든 문자(U+0000~U+FFFF)에서 요청 검증과 도메인이 같다")
	void sameRuleAsDomain() {
		List<String> mismatches = new ArrayList<>();
		for (int c = 0; c <= 0xFFFF; c++) {
			String title = String.valueOf((char) c);
			if (titleAccepted(title) != domainAccepts(title)) {
				mismatches.add(String.format("U+%04X", c));
			}
		}
		assertThat(mismatches).isEmpty();
	}

	private static boolean titleAccepted(String title) {
		return VALIDATOR.validateProperty(new CreatePostRequest("CARE", title, "본문"), "title").isEmpty();
	}

	private static boolean domainAccepts(String title) {
		try {
			Post.create(1L, CARE, title, "본문");
			return true;
		}
		catch (InvalidPostException e) {
			return false;
		}
	}

}
