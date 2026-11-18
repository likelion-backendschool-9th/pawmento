package com.pawmento.community.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	private final LoadPostCategoryPort loadPostCategoryPort;
	private final LoadPostPort loadPostPort;

	@Override
	@Transactional(readOnly = true)
	public PostPageResult list(ListPostsQuery query) {
		int size = query.size();
		PostCategory category = PostLookup.requireActiveCategory(loadPostCategoryPort, query.category());

		// 하나 더 읽어서 다음 페이지가 있는지 본다
		List<Post> found = loadPostPort.findPage(category.getId(), query.cursor(), size + 1);
		boolean hasNext = found.size() > size;
		List<Post> page = hasNext ? found.subList(0, size) : found;
		Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
		return new PostPageResult(page.stream().map(PostSummaryResult::from).toList(), nextCursor);
	}

}
