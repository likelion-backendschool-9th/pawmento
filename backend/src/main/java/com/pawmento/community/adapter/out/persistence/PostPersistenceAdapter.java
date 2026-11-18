package com.pawmento.community.adapter.out.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import com.pawmento.community.application.exception.PostConcurrentModificationException;
import com.pawmento.community.application.port.out.LoadPostCategoryPort;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.application.port.out.SavePostPort;
import com.pawmento.community.application.port.out.UpdatePostPort;
import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class PostPersistenceAdapter implements SavePostPort, UpdatePostPort, LoadPostPort, LoadPostCategoryPort {

	private final PostJpaRepository postJpaRepository;
	private final PostCategoryJpaRepository postCategoryJpaRepository;
	private final Clock clock;

	@Override
	public Long save(Post post) {
		return postJpaRepository.save(PostJpaEntity.create(post, now())).getId();
	}

	@Override
	public Post update(Post post) {
		try {
			PostJpaEntity saved = postJpaRepository.save(PostJpaEntity.changed(post, now()));
			// flush해야 @Version 충돌이 커밋 때가 아니라 여기서 난다
			postJpaRepository.flush();
			return saved.toDomain(post.getCategory());
		}
		catch (OptimisticLockingFailureException e) {
			// 읽은 뒤 다른 요청이 먼저 고치거나 지웠다. 지운 글이 오래된 수정으로 되살아나지 않게 거절한다
			throw new PostConcurrentModificationException();
		}
	}

	@Override
	public Optional<Post> findById(Long postId) {
		return postJpaRepository.findByIdAndDeletedAtIsNull(postId).map(this::toDomain);
	}

	@Override
	public List<Post> findPage(Long categoryId, Long cursor, int limit) {
		PostCategory category = requireCategory(categoryId);
		List<PostJpaEntity> found = cursor == null
				? postJpaRepository.findByCategoryIdAndDeletedAtIsNullOrderByIdDesc(categoryId, Limit.of(limit))
				: postJpaRepository.findByCategoryIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(categoryId, cursor,
						Limit.of(limit));
		return found.stream().map(entity -> entity.toDomain(category)).toList();
	}

	@Override
	public Optional<PostCategory> findByCode(String code) {
		return postCategoryJpaRepository.findByCode(code).map(PostCategoryJpaEntity::toDomain);
	}

	@Override
	public List<PostCategory> findAllActive() {
		return postCategoryJpaRepository.findByActiveTrueOrderBySortOrderAscIdAsc()
			.stream()
			.map(PostCategoryJpaEntity::toDomain)
			.toList();
	}

	private Post toDomain(PostJpaEntity entity) {
		return entity.toDomain(requireCategory(entity.getCategoryId()));
	}

	// 글이 가리키는 카테고리는 지우지 않으므로 항상 있다
	private PostCategory requireCategory(Long categoryId) {
		return postCategoryJpaRepository.findById(categoryId)
			.map(PostCategoryJpaEntity::toDomain)
			.orElseThrow(() -> new IllegalStateException("글의 카테고리가 없습니다: " + categoryId));
	}

	// DB는 마이크로초까지 저장한다. 저장 직후 돌려준 값과 다시 읽은 값이 같도록 맞춰 자른다
	private Instant now() {
		return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

}
