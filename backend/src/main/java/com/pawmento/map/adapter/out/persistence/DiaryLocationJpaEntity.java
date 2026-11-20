package com.pawmento.map.adapter.out.persistence;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;

import com.pawmento.global.exception.RuleViolationException;
import com.pawmento.map.application.exception.StoredLocationInvalidException;
import com.pawmento.map.domain.DiaryLocation;
import com.pawmento.map.domain.DiaryLocationState;
import com.pawmento.map.domain.DiaryLocationValue;
import com.pawmento.map.domain.LocationSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// 기록·반려동물·회원은 다른 도메인이므로 엔티티로 잇지 않고 id만 저장한다
@Entity
@Table(name = "diary_location",
		uniqueConstraints = @UniqueConstraint(name = DiaryLocationJpaEntity.DIARY_UNIQUE_CONSTRAINT,
				columnNames = "diary_id"),
		indexes = @Index(name = DiaryLocationJpaEntity.PIN_LOOKUP_INDEX,
				columnList = "created_by, pet_id, latitude, longitude"))
class DiaryLocationJpaEntity {

	// 기록당 위치 하나를 지키는 유일 제약. 이 제약을 어겼을 때만 "이미 있는 위치"로 본다
	static final String DIARY_UNIQUE_CONSTRAINT = "uk_diary_location_diary";

	// 핀 조회용 인덱스. 실행 계획은 PR에서 확인한 결과를 따른다
	static final String PIN_LOOKUP_INDEX = "idx_diary_location_pin_lookup";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long diaryId;

	@Column(nullable = false)
	private Long petId;

	@Column(nullable = false)
	private Long createdBy;

	@Column(nullable = false)
	private double latitude;

	@Column(nullable = false)
	private double longitude;

	@Column(length = DiaryLocation.PLACE_NAME_MAX_LENGTH)
	private String placeName;

	// 위치의 출처와 마지막으로 반영한 revision. 이미 행이 있는 테이블에 열을 더할 때 ddl-auto update가 NOT NULL 열을 기본값 없이
	// 추가하지 못하므로, 기존 행에 채울 값을 열 기본값(@ColumnDefault)으로 둔다. 기존 행은 직접 고른 위치(MANUAL)이고 revision은 0이다
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@ColumnDefault("'MANUAL'")
	private LocationSource source = LocationSource.MANUAL;

	// 사진에서 고른 위치일 때의 자산 id. 직접 고른 위치는 비어 있다
	private Long sourceAssetId;

	@Column(nullable = false)
	@ColumnDefault("0")
	private long sourceRevision;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected DiaryLocationJpaEntity() {
	}

	// 정정 경로에서는 손상된 식별자·메타데이터도 일기 원본으로 바꾼다.
	void replaceState(
			Long diaryId, Long petId, Long createdBy, long revision, DiaryLocationValue location) {
		this.diaryId = diaryId;
		this.petId = petId;
		this.createdBy = createdBy;
		this.latitude = location.latitude();
		this.longitude = location.longitude();
		this.placeName = location.placeName();
		this.source = LocationSource.MANUAL;
		this.sourceAssetId = null;
		this.sourceRevision = revision;
	}

	// 정정과 삭제는 손상된 메타데이터도 관측해야 하므로 도메인 복원 검사를 적용하지 않는다.
	DiaryLocationState toState() {
		return new DiaryLocationState(
				id,
				diaryId,
				petId,
				createdBy,
				latitude,
				longitude,
				placeName,
				source,
				sourceAssetId,
				sourceRevision);
	}

	// 저장된 행이 도메인 규칙에 맞지 않으면 요청 입력 오류(400)가 아니라 저장 데이터 오류(500)다.
	// 예: DB의 varchar(255)에는 들어가지만 Java String.length()로는 255를 넘는 이모지 이름.
	// 원인은 cause로 보존한다. StoredDataInvalidException을 전역 핸들러가 500으로 명시적으로 처리하므로
	// Spring MVC가 cause의 RuleViolationException(400 핸들러)을 따라가지 않는다
	DiaryLocation toDomain() {
		try {
			return DiaryLocation.restore(id, diaryId, petId, createdBy, latitude, longitude, placeName, source,
					sourceAssetId, sourceRevision);
		}
		catch (RuleViolationException e) {
			throw new StoredLocationInvalidException("저장된 위치가 도메인 규칙에 맞지 않습니다. id=" + id, e);
		}
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	Long getId() {
		return id;
	}

}
