package com.pawmento.map.adapter.out.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.exception.PlacePersistenceUnavailableException;
import com.pawmento.map.application.port.out.AppliedPlaces;
import com.pawmento.map.application.port.out.LoadPlaceByIdPort;
import com.pawmento.map.application.port.out.LoadPlaceDataAsOfPort;
import com.pawmento.map.application.port.out.LoadPlacesPort;
import com.pawmento.map.application.port.out.StorePlacesPort;
import com.pawmento.map.domain.MapArea;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class PlacePersistenceAdapter implements LoadPlacesPort, LoadPlaceByIdPort, StorePlacesPort, LoadPlaceDataAsOfPort {

	private final PlaceJpaRepository placeJpaRepository;

	private final PlaceSyncRunJpaRepository placeSyncRunJpaRepository;

	private final Clock clock;

	@Override
	public List<Place> loadInArea(MapArea area, Set<PlaceCategory> categories) {
		if (area == null || categories == null || categories.isEmpty()) {
			throw new IllegalArgumentException("영역과 종류가 필요합니다.");
		}
		return MapPersistenceOperations.execute(() -> placeJpaRepository
			.findInArea(categories, area.getMinLatitude(), area.getMinLongitude(), area.getMaxLatitude(),
					area.getMaxLongitude())
			.stream()
			.map(PlaceJpaEntity::toDomain)
			.toList(), PlacePersistenceUnavailableException::new);
	}

	@Override
	public Optional<Place> loadById(Long placeId) {
		if (placeId == null) {
			throw new IllegalArgumentException("장소 id가 필요합니다.");
		}
		return MapPersistenceOperations.execute(
				() -> placeJpaRepository.findById(placeId).map(PlaceJpaEntity::toDomain),
				PlacePersistenceUnavailableException::new);
	}

	@Override
	public Map<PlaceCategory, Instant> syncedAt(Set<PlaceCategory> categories) {
		if (categories == null || categories.isEmpty()) {
			throw new IllegalArgumentException("종류가 필요합니다.");
		}
		return MapPersistenceOperations.execute(() -> {
			Map<PlaceCategory, Instant> synced = new EnumMap<>(PlaceCategory.class);
			placeSyncRunJpaRepository.findAllById(categories)
				.forEach(run -> synced.put(run.getCategory(), run.getLastSyncedAt()));
			return synced;
		}, PlacePersistenceUnavailableException::new);
	}

	@Override
	public int countByCategory(PlaceCategory category) {
		return MapPersistenceOperations.execute(() -> (int) placeJpaRepository.countByCategory(category),
				PlacePersistenceUnavailableException::new);
	}

	// 추가·갱신·삭제를 한 트랜잭션으로 한다. 도중에 실패하면 모두 되돌아가서 지도가 일부만 바뀌는 일이 없다(#172-S5).
	// 데이터베이스 오류를 이 메서드 안에서 만나도록 중간에 flush한다(커밋 때 나는 오류는 응용 예외로 바꿀 수 없어서)
	@Override
	@Transactional
	public AppliedPlaces replace(PlaceCategory category, List<PlaceDraft> drafts) {
		return MapPersistenceOperations.execute(() -> {
			Map<String, PlaceJpaEntity> existing = new HashMap<>();
			placeJpaRepository.findByCategory(category).forEach(entity -> existing.put(entity.getSourceId(), entity));

			int inserted = 0;
			int updated = 0;
			Set<String> incoming = new HashSet<>();
			List<PlaceJpaEntity> toInsert = new ArrayList<>();
			for (PlaceDraft draft : drafts) {
				incoming.add(draft.sourceId());
				PlaceJpaEntity entity = existing.get(draft.sourceId());
				if (entity == null) {
					toInsert.add(PlaceJpaEntity.from(draft));
					inserted++;
				}
				else if (entity.apply(draft)) {
					updated++;
				}
			}
			List<Long> toDelete = existing.entrySet()
				.stream()
				.filter(entry -> !incoming.contains(entry.getKey()))
				.map(entry -> entry.getValue().getId())
				.toList();

			placeJpaRepository.saveAll(toInsert);
			if (!toDelete.isEmpty()) {
				placeJpaRepository.deleteAllByIdInBatch(toDelete);
			}
			placeJpaRepository.flush();
			// 가져온 시각은 같은 트랜잭션에서 남긴다. 반영이 되돌아가면 시각도 남지 않고, 바뀐 것이 없어도 가져오기에 성공했다면 갱신한다
			Instant now = clock.instant();
			PlaceSyncRunJpaEntity run = placeSyncRunJpaRepository.findById(category)
				.orElseGet(() -> new PlaceSyncRunJpaEntity(category, now));
			run.syncedAt(now);
			placeSyncRunJpaRepository.saveAndFlush(run);
			return new AppliedPlaces(inserted, updated, toDelete.size());
		}, PlacePersistenceUnavailableException::new);
	}

}
