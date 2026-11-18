package com.pawmento.community.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.community.application.port.in.ListPostCategoriesUseCase;
import com.pawmento.community.application.port.in.PostCategoryResult;
import com.pawmento.community.application.port.out.LoadPostCategoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ListPostCategoriesService implements ListPostCategoriesUseCase {

	private final LoadPostCategoryPort loadPostCategoryPort;

	@Override
	@Transactional(readOnly = true)
	public List<PostCategoryResult> list() {
		return loadPostCategoryPort.findAllActive().stream().map(PostCategoryResult::from).toList();
	}

}
