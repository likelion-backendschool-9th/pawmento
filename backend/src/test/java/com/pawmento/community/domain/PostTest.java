package com.pawmento.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.community.domain.exception.InvalidPostException;
import com.pawmento.community.domain.exception.NotPostAuthorException;
import com.pawmento.global.exception.PermissionDeniedException;
import com.pawmento.global.exception.RuleViolationException;

class PostTest {

	private static final Long AUTHOR = 1L;
	private static final Long OTHER = 2L;
	private static final PostCategory CARE = PostCategory.restore(2L, "CARE", "건강·돌봄", 2, true);
	private static final PostCategory MEMORIAL = PostCategory.restore(4L, "MEMORIAL", "추모 방식", 4, true);

	private Post post() {
		return Post.create(AUTHOR, CARE, "노견 관절 영양제 후기", "3개월 먹였어요");
	}

	@Test
	@DisplayName("#21-S1 앞뒤 공백을 지우고 글을 만든다")
	void createStripsText() {
		Post post = Post.create(AUTHOR, CARE, "  노견 관절 영양제 후기 ", "\n3개월 먹였어요\n");

		assertThat(post.getId()).isNull();
		assertThat(post.getAuthorId()).isEqualTo(AUTHOR);
		assertThat(post.getCategory()).isEqualTo(CARE);
		assertThat(post.getTitle()).isEqualTo("노견 관절 영양제 후기");
		assertThat(post.getBody()).isEqualTo("3개월 먹였어요");
		assertThat(post.isDeleted()).isFalse();
	}

	@Test
	@DisplayName("#21-S2 빈 제목·본문과 길이를 넘는 제목·본문을 거부한다")
	void createRejectsInvalidText() {
		String title100 = "가".repeat(100);
		String body5000 = "가".repeat(5000);

		assertThat(Post.create(AUTHOR, CARE, title100, body5000).getTitle()).hasSize(100);
		assertThatThrownBy(() -> Post.create(AUTHOR, CARE, "   ", "본문")).isInstanceOf(InvalidPostException.class)
			.isInstanceOf(RuleViolationException.class);
		assertThatThrownBy(() -> Post.create(AUTHOR, CARE, null, "본문")).isInstanceOf(InvalidPostException.class);
		assertThatThrownBy(() -> Post.create(AUTHOR, CARE, title100 + "가", "본문"))
			.isInstanceOf(InvalidPostException.class);
		assertThatThrownBy(() -> Post.create(AUTHOR, CARE, "제목", " ")).isInstanceOf(InvalidPostException.class);
		assertThatThrownBy(() -> Post.create(AUTHOR, CARE, "제목", body5000 + "가"))
			.isInstanceOf(InvalidPostException.class);
	}

	@Test
	@DisplayName("#21-S2 줄바꿈 없는 공백(U+00A0)과 BOM도 프론트엔드 trim()처럼 공백으로 본다")
	void nonBreakingSpaceIsBlank() {
		assertThatThrownBy(() -> Post.create(AUTHOR, CARE, "\u00A0\u00A0", "본문"))
			.isInstanceOf(InvalidPostException.class);
		assertThatThrownBy(() -> Post.create(AUTHOR, CARE, "제목", "\uFEFF\u202F")).isInstanceOf(InvalidPostException.class);

		Post post = Post.create(AUTHOR, CARE, "\u00A0제목\u3000", "본문");
		assertThat(post.getTitle()).isEqualTo("제목");
	}

	@Test
	@DisplayName("#21-S2 프론트엔드 trim()이 남기는 제어 문자(U+001C~U+001F)는 지우지 않는다")
	void separatorControlCharsAreNotSpace() {
		Post post = Post.create(AUTHOR, CARE, "\u001C제목\u001F", "\u001D");
		assertThat(post.getTitle()).isEqualTo("\u001C제목\u001F");
		assertThat(post.getBody()).isEqualTo("\u001D");
	}

	@Test
	@DisplayName("#21-S2 작성자나 카테고리가 없으면 거부한다")
	void createRequiresAuthorAndCategory() {
		assertThatThrownBy(() -> Post.create(null, CARE, "제목", "본문")).isInstanceOf(InvalidPostException.class);
		assertThatThrownBy(() -> Post.create(AUTHOR, null, "제목", "본문")).isInstanceOf(InvalidPostException.class);
	}

	@Test
	@DisplayName("#21-S8 작성자는 카테고리·제목·본문을 모두 고친다")
	void authorEdits() {
		Post post = post();

		post.edit(AUTHOR, MEMORIAL, " 고친 제목 ", "고친 본문");

		assertThat(post.getCategory()).isEqualTo(MEMORIAL);
		assertThat(post.getTitle()).isEqualTo("고친 제목");
		assertThat(post.getBody()).isEqualTo("고친 본문");
	}

	@Test
	@DisplayName("#21-S8 규칙에 맞지 않는 수정은 아무것도 바꾸지 않는다")
	void invalidEditKeepsState() {
		Post post = post();

		assertThatThrownBy(() -> post.edit(AUTHOR, MEMORIAL, "고친 제목", " ")).isInstanceOf(InvalidPostException.class);

		assertThat(post.getCategory()).isEqualTo(CARE);
		assertThat(post.getTitle()).isEqualTo("노견 관절 영양제 후기");
		assertThat(post.getBody()).isEqualTo("3개월 먹였어요");
	}

	@Test
	@DisplayName("#21-S9 작성자가 지우면 지운 글이 된다")
	void authorDeletes() {
		Post post = post();

		post.delete(AUTHOR);

		assertThat(post.isDeleted()).isTrue();
	}

	@Test
	@DisplayName("#21-S10 작성자가 아니면 고치거나 지울 수 없고 글은 그대로다")
	void otherMemberIsRejected() {
		Post post = post();

		assertThatThrownBy(() -> post.edit(OTHER, MEMORIAL, "고친 제목", "고친 본문"))
			.isInstanceOf(NotPostAuthorException.class)
			.isInstanceOf(PermissionDeniedException.class);
		assertThatThrownBy(() -> post.delete(OTHER)).isInstanceOf(NotPostAuthorException.class);
		assertThatThrownBy(() -> post.delete(null)).isInstanceOf(NotPostAuthorException.class);

		assertThat(post.getTitle()).isEqualTo("노견 관절 영양제 후기");
		assertThat(post.getCategory()).isEqualTo(CARE);
		assertThat(post.isDeleted()).isFalse();
	}

}
