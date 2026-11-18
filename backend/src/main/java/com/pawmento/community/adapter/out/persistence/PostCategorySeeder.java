package com.pawmento.community.adapter.out.persistence;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 서버가 시작할 때 처음 카테고리 5개를 넣는다. 같은 code가 이미 있으면 건드리지 않아,
// DB에서 바꾼 이름·순서·사용 여부가 재시작 뒤에도 남는다. 마이그레이션 도구가 정해지면 그쪽으로 옮긴다
@Component
@RequiredArgsConstructor
class PostCategorySeeder implements ApplicationRunner {

	private record Seed(String code, String name, int sortOrder) {
	}

	private static final List<Seed> SEEDS = List.of(
			new Seed("LIFE", "반려 생활", 1),
			new Seed("CARE", "건강·돌봄", 2),
			new Seed("FUNERAL", "장례 정보", 3),
			new Seed("MEMORIAL", "추모 방식", 4),
			new Seed("MEMORY", "기억 나누기", 5));

	private final PostCategoryJpaRepository postCategoryJpaRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		for (Seed seed : SEEDS) {
			if (!postCategoryJpaRepository.existsByCode(seed.code())) {
				postCategoryJpaRepository.save(PostCategoryJpaEntity.of(seed.code(), seed.name(), seed.sortOrder()));
			}
		}
	}

}
