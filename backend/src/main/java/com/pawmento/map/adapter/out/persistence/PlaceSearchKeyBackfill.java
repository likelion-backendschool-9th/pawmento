package com.pawmento.map.adapter.out.persistence;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.pawmento.map.domain.Place;

import lombok.RequiredArgsConstructor;

/**
 * 검색용 열(name_key, address_key)을 더하기 전에 저장한 장소의 열을 서버가 시작할 때 채운다(#195-S19).
 * 비어 있는 행만 고치고 이미 채워진 행은 건드리지 않는다. 열이 비어 있지 않으면 읽는 행이 없어 바로 끝난다.
 *
 * <p>행을 읽은 뒤 저장하는 방식이 아니라, 갱신할 때 {@code name_key is null}을 다시 확인하는 조건 갱신으로 두 열만 채운다
 * ({@link PlaceJpaRepository#fillSearchKeys}). 읽은 뒤에 장소 가져오기가 같은 행의 이름·주소를 갱신했다면 가져오기가 이미 열을
 * 채웠으므로 아무것도 바꾸지 않아, 최신 장소 정보를 옛 값으로 되돌릴 일이 없다. 각 갱신은 한 문장이라 따로 트랜잭션을 묶지 않는다.
 * 채우기에 실패해도 서버 시작은 막지 않는다. 비어 있는 행은 다음에 켜질 때 다시 채우고, 그동안 그 장소는 검색에만 안 잡힌다.
 */
@Component
@RequiredArgsConstructor
class PlaceSearchKeyBackfill implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PlaceSearchKeyBackfill.class);

	private final PlaceJpaRepository placeJpaRepository;

	@Override
	public void run(ApplicationArguments args) {
		try {
			int filled = backfill();
			if (filled > 0) {
				log.info("장소 {}곳의 검색용 열을 채웠습니다.", filled);
			}
		}
		catch (RuntimeException e) {
			log.warn("장소의 검색용 열을 채우지 못했습니다. 다음에 켜질 때 다시 채웁니다: {}", e.getClass().getSimpleName());
		}
	}

	/** 비어 있는 행의 검색용 열을 채우고, 이번에 실제로 채운 행의 수를 돌려준다. */
	int backfill() {
		int filled = 0;
		List<PlaceJpaEntity> batch = placeJpaRepository.findTop500ByNameKeyIsNull();
		while (!batch.isEmpty()) {
			for (PlaceJpaEntity entity : batch) {
				if (fill(entity)) {
					filled++;
				}
			}
			// 채운 행과 가져오기가 먼저 채운 행은 모두 name_key가 비어 있지 않아 다음 조회에서 빠진다
			batch = placeJpaRepository.findTop500ByNameKeyIsNull();
		}
		return filled;
	}

	/** 읽은 값으로 두 열을 채운다. 그 사이 다른 곳이 먼저 채웠으면 아무것도 바꾸지 않고 false다. */
	boolean fill(PlaceJpaEntity entity) {
		Place place = entity.toDomain();
		return placeJpaRepository.fillSearchKeys(entity.getId(), place.nameKey(), place.addressKey()) > 0;
	}

}
