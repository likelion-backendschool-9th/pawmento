package com.pawmento.community.application.port.in;

import java.util.List;

public interface ListPostCategoriesUseCase {

	// 사용 중인 카테고리를 탭 순서대로 돌려준다
	List<PostCategoryResult> list();

}
