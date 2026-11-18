package com.pawmento.community.adapter.out.persistence;

import com.pawmento.community.domain.PostCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "post_categories",
		uniqueConstraints = @UniqueConstraint(name = "uk_post_categories_code", columnNames = "code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PostCategoryJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 30)
	private String code;

	@Column(nullable = false, length = 30)
	private String name;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean active;

	static PostCategoryJpaEntity of(String code, String name, int sortOrder) {
		PostCategoryJpaEntity entity = new PostCategoryJpaEntity();
		entity.code = code;
		entity.name = name;
		entity.sortOrder = sortOrder;
		entity.active = true;
		return entity;
	}

	PostCategory toDomain() {
		return PostCategory.restore(id, code, name, sortOrder, active);
	}

}
