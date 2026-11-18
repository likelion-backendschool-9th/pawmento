package com.pawmento.community.adapter.out.persistence;

import java.time.Instant;

import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 카테고리는 연관관계 대신 id로 가리킨다. 카테고리는 몇 개뿐이라 어댑터가 따로 읽어 붙인다
@Entity
@Table(name = "posts", indexes = @Index(name = "idx_posts_category_id", columnList = "category_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PostJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "author_id", nullable = false, updatable = false)
	private Long authorId;

	@Column(name = "category_id", nullable = false)
	private Long categoryId;

	@Column(nullable = false, length = Post.TITLE_MAX_LENGTH)
	private String title;

	@Column(nullable = false, length = Post.BODY_MAX_LENGTH)
	private String body;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	// 지우지 않은 글은 null이다
	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Version
	private Long version;

	// 새 글. 시각은 어댑터가 정해서 넣는다
	static PostJpaEntity create(Post post, Instant now) {
		PostJpaEntity entity = new PostJpaEntity();
		entity.authorId = post.getAuthorId();
		entity.categoryId = post.getCategory().getId();
		entity.title = post.getTitle();
		entity.body = post.getBody();
		entity.createdAt = now;
		entity.updatedAt = now;
		return entity;
	}

	// 불러온 글의 바뀐 값을 옮긴다. 도메인이 들고 온 버전을 그대로 넣어, 그사이 다른 요청이 저장했으면 충돌하게 한다
	static PostJpaEntity changed(Post post, Instant now) {
		PostJpaEntity entity = new PostJpaEntity();
		entity.id = post.getId();
		entity.authorId = post.getAuthorId();
		entity.categoryId = post.getCategory().getId();
		entity.title = post.getTitle();
		entity.body = post.getBody();
		entity.createdAt = post.getCreatedAt();
		entity.version = post.getVersion();
		if (post.isDeleted()) {
			// 지울 때는 고친 시각을 바꾸지 않는다
			entity.updatedAt = post.getUpdatedAt();
			entity.deletedAt = now;
		}
		else {
			entity.updatedAt = now;
		}
		return entity;
	}

	Post toDomain(PostCategory category) {
		return Post.restore(id, authorId, category, title, body, createdAt, updatedAt, deletedAt != null, version);
	}

}
