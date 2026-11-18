package com.pawmento.community.adapter.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.community.application.port.in.CreatePostCommand;
import com.pawmento.community.application.port.in.CreatePostUseCase;
import com.pawmento.community.application.port.in.GetPostQuery;
import com.pawmento.community.application.port.in.GetPostUseCase;
import com.pawmento.community.application.port.in.ListPostCategoriesUseCase;
import com.pawmento.community.application.port.in.ListPostsQuery;
import com.pawmento.community.application.port.in.ListPostsUseCase;
import com.pawmento.global.security.LoginMember;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 목록·상세·카테고리 목록은 로그인 없이 부른다(PublicEndpoints.PUBLIC_READ). 글 쓰기는 로그인이 필요하다
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

	private final CreatePostUseCase createPostUseCase;
	private final ListPostsUseCase listPostsUseCase;
	private final GetPostUseCase getPostUseCase;
	private final ListPostCategoriesUseCase listPostCategoriesUseCase;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CreatePostResponse create(@LoginMember Long memberId, @Valid @RequestBody CreatePostRequest request) {
		return new CreatePostResponse(createPostUseCase
			.create(new CreatePostCommand(memberId, request.category(), request.title(), request.body())));
	}

	// 카테고리는 필수다. 전체 목록은 없다
	@GetMapping
	PostPageResponse list(@RequestParam String category, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) Long cursor) {
		return PostPageResponse.from(listPostsUseCase.list(new ListPostsQuery(category, size, cursor)));
	}

	// 고정 경로라 Spring이 /{postId}보다 먼저 고른다
	@GetMapping("/categories")
	List<PostCategoryResponse> categories() {
		return listPostCategoriesUseCase.list().stream().map(PostCategoryResponse::from).toList();
	}

	@GetMapping("/{postId}")
	PostResponse get(@PathVariable Long postId) {
		return PostResponse.from(getPostUseCase.get(new GetPostQuery(postId)));
	}

}
