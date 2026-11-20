package com.pawmento.map.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.pawmento.map.application.exception.DiaryLocationPersistenceUnavailableException;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;
import com.pawmento.map.application.port.out.LoadMapPinsPort;
import com.pawmento.map.domain.DiaryLocation;
import com.pawmento.map.domain.DiaryLocationState;
import com.pawmento.map.domain.DiaryLocationValue;
import com.pawmento.map.domain.MapArea;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Component
class DiaryLocationPersistenceAdapter
		implements LoadMapPinsPort,
			DiaryLocationStorePort {

	private final DiaryLocationJpaRepository diaryLocationJpaRepository;

	private final EntityManager entityManager;

	DiaryLocationPersistenceAdapter(DiaryLocationJpaRepository diaryLocationJpaRepository, EntityManager entityManager) {
		this.diaryLocationJpaRepository = diaryLocationJpaRepository;
		this.entityManager = entityManager;
	}

	@Override
	public Optional<DiaryLocationState> read(Long diaryId) {
		return execute(
				() ->
						diaryLocationJpaRepository
								.findByDiaryId(diaryId)
								.map(
										entity -> {
											entityManager.refresh(entity);
											return entity.toState();
										}));
	}

	@Override
	public Optional<DiaryLocationState> lock(Long diaryId) {
		return execute(
				() -> {
					// 행이 없는 최초 삽입도 직렬화한다. 데이터 읽기·쓰기는 모두 같은 JPA 문맥을 쓴다.
					entityManager
							.createNativeQuery(
									"select true from"
										+ " pg_advisory_xact_lock(hashtextextended(:key,0))",
									Boolean.class)
							.setParameter("key", "pawmento-map-location:" + diaryId)
							.getResultList();
					return diaryLocationJpaRepository
							.findByDiaryId(diaryId)
							.map(
									entity -> {
										// 사전 조회가 남긴 엔티티도 잠금을 얻은 시점의 DB 값으로 다시 읽는다.
										entityManager.refresh(
												entity, LockModeType.PESSIMISTIC_WRITE);
										return entity.toState();
									});
				});
	}

	@Override
	public void write(
			Long diaryId, Long petId, Long createdBy, long revision, DiaryLocationValue location) {
		execute(
				() -> {
					var entity =
							diaryLocationJpaRepository
									.findByDiaryId(diaryId)
									.orElseGet(DiaryLocationJpaEntity::new);
					entity.replaceState(diaryId, petId, createdBy, revision, location);
					diaryLocationJpaRepository.saveAndFlush(entity);
				});
	}

	@Override
	public void remove(Long diaryId) {
		execute(
				() -> {
					diaryLocationJpaRepository
							.findByDiaryId(diaryId)
							.ifPresent(diaryLocationJpaRepository::delete);
					diaryLocationJpaRepository.flush();
				});
	}

	@Override
	public List<DiaryLocation> loadInArea(Long createdBy, Long petId, MapArea area, int limit) {
		if (createdBy == null || petId == null || area == null) {
			throw new IllegalArgumentException("회원, 반려동물, 영역이 필요합니다.");
		}
		if (limit < 1) {
			throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
		}
		return execute(() -> diaryLocationJpaRepository
			.findInArea(createdBy, petId, area.getMinLatitude(), area.getMinLongitude(), area.getMaxLatitude(),
					area.getMaxLongitude(), PageRequest.of(0, limit))
			.stream()
			.map(DiaryLocationJpaEntity::toDomain)
			.toList());
	}

	// 이 어댑터의 모든 저장소 호출은 같은 응용 예외로 바꾼다
	private static <T> T execute(Supplier<T> action) {
		return MapPersistenceOperations.execute(action, DiaryLocationPersistenceUnavailableException::new);
	}

	private static void execute(Runnable action) {
		MapPersistenceOperations.execute(action, DiaryLocationPersistenceUnavailableException::new);
	}

}
