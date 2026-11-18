package com.pawmento.community.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.community.application.exception.InvalidPostPageException;
import com.pawmento.community.application.port.in.ListPostsQuery;
import com.pawmento.community.application.port.in.ListPostsUseCase;
import com.pawmento.community.application.port.in.PostPageResult;
import com.pawmento.community.application.port.in.PostSummaryResult;
import com.pawmento.community.application.port.out.LoadPostCategoryPort;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ListPostsService implements ListPostsUseCase {

	static final int DEFAULT_SIZE = 20;
	static final int MAX_SIZE = 50;

	private final LoadPostCategoryPort loadPostCategoryPort;
	private final LoadPostPort loadPostPort;

	@Override
	@Transactional(readOnly = true)
	public PostPageResult list(ListPostsQuery query) {
		int size = requireSize(query.size());
		requireCursor(query.cursor());
		PostCategory category = PostLookup.requireActiveCategory(loadPostCategoryPort, query.category());

		// 하나 더 읽어서 다음 페이지가 있는지 본다
		List<Post> found = loadPostPort.findPage(category.getId(), query.cursor(), size + 1);
		boolean hasNext = found.size() > size;
		List<Post> page = hasNext ? found.subList(0, size) : found;
		Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
		return new PostPageResult(page.stream().map(PostSummaryResult::from).toList(), nextCursor);
	}

	// 범위를 벗어나면 보정하지 않고 거절한다. 프론트엔드는 정해진 값만 보내므로 잘못된 호출을 드러낸다
	private static int requireSize(Integer size) {
		if (size == null) {
			return DEFAULT_SIZE;
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new InvalidPostPageException("size는 1~" + MAX_SIZE + " 사이로 보내 주세요.");
		}
		return size;
	}

	private static void requireCursor(Long cursor) {
		if (cursor != null && cursor < 1) {
			throw new InvalidPostPageException("cursor는 1 이상으로 보내 주세요.");
		}
	}

}
