package com.pawmento.map.adapter.out.persistence;

import java.time.Instant;

import com.pawmento.map.domain.RecordLocation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "record_location",
		uniqueConstraints = @UniqueConstraint(name = RecordLocationJpaEntity.RECORD_UNIQUE_CONSTRAINT,
				columnNames = "record_id"),
		indexes = @Index(name = RecordLocationJpaEntity.PIN_LOOKUP_INDEX,
				columnList = "created_by, pet_id, latitude, longitude"))
class RecordLocationJpaEntity {

	// 기록당 위치 하나를 지키는 유일 제약. 이 제약을 어겼을 때만 "이미 있는 위치"로 본다
	static final String RECORD_UNIQUE_CONSTRAINT = "uk_record_location_record";

	// 핀 조회용 인덱스. 실행 계획은 PR에서 확인한 결과를 따른다
	static final String PIN_LOOKUP_INDEX = "idx_record_location_pin_lookup";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "record_id", nullable = false)
	private Long recordId;

	@Column(name = "pet_id", nullable = false)
	private Long petId;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(nullable = false)
	private double latitude;

	@Column(nullable = false)
	private double longitude;

	@Column(name = "place_name", length = RecordLocation.PLACE_NAME_MAX_LENGTH)
	private String placeName;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected RecordLocationJpaEntity() {
	}

	static RecordLocationJpaEntity from(RecordLocation location) {
		RecordLocationJpaEntity entity = new RecordLocationJpaEntity();
		entity.recordId = location.getRecordId();
		entity.petId = location.getPetId();
		entity.createdBy = location.getCreatedBy();
		entity.updateLocation(location);
		return entity;
	}

	// 좌표와 장소 이름만 바꾼다. 기록·반려동물·작성자는 바뀌지 않는다
	void updateLocation(RecordLocation location) {
		this.latitude = location.getLatitude();
		this.longitude = location.getLongitude();
		this.placeName = location.getPlaceName();
	}

	// 기록·반려동물·작성자 id가 모두 같은지 비교한다. 권한 검사가 아니라 같은 위치 행인지 확인하는 용도다
	boolean hasSameReferences(RecordLocation location) {
		return recordId.equals(location.getRecordId()) && petId.equals(location.getPetId())
				&& createdBy.equals(location.getCreatedBy());
	}

	RecordLocation toDomain() {
		return RecordLocation.restore(id, recordId, petId, createdBy, latitude, longitude, placeName);
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
