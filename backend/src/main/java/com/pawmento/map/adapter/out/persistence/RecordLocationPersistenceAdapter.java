package com.pawmento.map.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.pawmento.map.application.exception.RecordLocationAlreadyExistsException;
import com.pawmento.map.application.exception.RecordLocationPersistenceUnavailableException;
import com.pawmento.map.application.port.out.DeleteRecordLocationPort;
import com.pawmento.map.application.port.out.LoadMapPinsPort;
import com.pawmento.map.application.port.out.LoadRecordLocationPort;
import com.pawmento.map.application.port.out.SaveRecordLocationPort;
import com.pawmento.map.domain.MapArea;
import com.pawmento.map.domain.RecordLocation;

@Component
class RecordLocationPersistenceAdapter
		implements SaveRecordLocationPort, LoadRecordLocationPort, DeleteRecordLocationPort, LoadMapPinsPort {

	private final RecordLocationJpaRepository recordLocationJpaRepository;

	RecordLocationPersistenceAdapter(RecordLocationJpaRepository recordLocationJpaRepository) {
		this.recordLocationJpaRepository = recordLocationJpaRepository;
	}

	@Override
	public Long save(RecordLocation location) {
		return execute(() -> {
			RecordLocationJpaEntity entity;
			if (location.getId() == null) {
				entity = RecordLocationJpaEntity.from(location);
			}
			else {
				entity = recordLocationJpaRepository.findById(location.getId())
					.orElseThrow(() -> new IllegalStateException("저장된 위치를 찾을 수 없습니다. id=" + location.getId()));
				// 기록·반려동물·작성자는 바뀌지 않는다. 다른 위치의 id로 덮어쓰는 일을 막는다
				if (!entity.hasSameReferences(location)) {
					throw new IllegalStateException("저장된 위치와 기록·반려동물·작성자가 다릅니다. id=" + location.getId());
				}
				entity.updateLocation(location);
			}
			try {
				return recordLocationJpaRepository.save(entity).getId();
			}
			catch (DataIntegrityViolationException e) {
				// 기록당 위치는 하나다. 처음 저장하는 요청이 겹치면 DB의 유일 제약이 막는다.
				// 그 제약을 어긴 경우만 "이미 있는 위치"로 보고, 다른 무결성 오류는 저장소 오류로 바뀐다
				if (isRecordUniqueViolation(e)) {
					throw new RecordLocationAlreadyExistsException(location.getRecordId(), e);
				}
				throw e;
			}
		});
	}

	private boolean isRecordUniqueViolation(DataIntegrityViolationException e) {
		return e.getCause() instanceof ConstraintViolationException violation
				&& RecordLocationJpaEntity.RECORD_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
	}

	@Override
	public Optional<RecordLocation> loadByRecordId(Long recordId) {
		return execute(() -> recordLocationJpaRepository.findByRecordId(recordId).map(RecordLocationJpaEntity::toDomain));
	}

	@Override
	public void deleteByRecordId(Long recordId) {
		// 엔티티를 찾아 지운다. 한 번에 지우는 쿼리와 달리 영속성 컨텍스트와 어긋나지 않는다
		execute(() -> recordLocationJpaRepository.findByRecordId(recordId).ifPresent(recordLocationJpaRepository::delete));
	}

	@Override
	public List<RecordLocation> loadInArea(Long createdBy, Long petId, MapArea area, int limit) {
		if (createdBy == null || petId == null || area == null) {
			throw new IllegalArgumentException("회원, 반려동물, 영역이 필요합니다.");
		}
		if (limit < 1) {
			throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
		}
		return execute(() -> recordLocationJpaRepository
			.findInArea(createdBy, petId, area.getMinLatitude(), area.getMinLongitude(), area.getMaxLatitude(),
					area.getMaxLongitude(), PageRequest.of(0, limit))
			.stream()
			.map(RecordLocationJpaEntity::toDomain)
			.toList());
	}

	// 이 어댑터의 모든 저장소 호출은 같은 응용 예외로 바꾼다
	private static <T> T execute(Supplier<T> action) {
		return MapPersistenceOperations.execute(action, RecordLocationPersistenceUnavailableException::new);
	}

	private static void execute(Runnable action) {
		MapPersistenceOperations.execute(action, RecordLocationPersistenceUnavailableException::new);
	}

}
