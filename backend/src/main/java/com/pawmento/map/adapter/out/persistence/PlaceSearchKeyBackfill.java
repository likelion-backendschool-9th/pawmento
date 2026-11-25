package com.pawmento.map.adapter.out.persistence;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 검색용 열(name_key, address_key)을 더하기 전에 저장한 장소의 열을 서버가 시작할 때 채운다(#195-S19).
 * 비어 있는 행만 고치고 이미 채워진 행은 건드리지 않는다. 열이 비어 있지 않으면 읽는 행이 없어 바로 끝난다.
 * 장소의 내용이 바뀐 것이 아니라서 다음 가져오기에서도 바뀐 것으로 세지 않는다(가져오기는 이름·주소 같은 원본 값이 달라질 때만 갱신으로 센다).
 */
@Component
@RequiredArgsConstructor
class PlaceSearchKeyBackfill implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PlaceSearchKeyBackfill.class);

	private final PlaceJpaRepository placeJpaRepository;

	@Override
	public void run(ApplicationArguments args) {
		int filled = backfill();
		if (filled > 0) {
			log.info("장소 {}곳의 검색용 열을 채웠습니다.", filled);
		}
	}

	/** 비어 있는 행의 검색용 열을 채우고 채운 행의 수를 돌려준다. */
	@Transactional
	int backfill() {
		int filled = 0;
		List<PlaceJpaEntity> batch = placeJpaRepository.findTop500ByNameKeyIsNull();
		while (!batch.isEmpty()) {
			batch.forEach(PlaceJpaEntity::fillSearchKeys);
			placeJpaRepository.saveAllAndFlush(batch);
			filled += batch.size();
			batch = placeJpaRepository.findTop500ByNameKeyIsNull();
		}
		return filled;
	}

}
