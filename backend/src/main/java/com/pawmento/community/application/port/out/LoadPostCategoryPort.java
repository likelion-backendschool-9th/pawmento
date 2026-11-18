package com.pawmento.community.application.port.out;

import java.util.List;
import java.util.Optional;

import com.pawmento.community.domain.PostCategory;

public interface LoadPostCategoryPort {

	// 사용 여부와 상관없이 코드로 찾는다
	Optional<PostCategory> findByCode(String code);

	// 사용 중인 카테고리를 탭 순서(sortOrder)대로 돌려준다
	List<PostCategory> findAllActive();

}
