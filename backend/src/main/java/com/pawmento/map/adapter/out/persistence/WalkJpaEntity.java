package com.pawmento.map.adapter.out.persistence;

import java.time.Instant;

import com.pawmento.map.domain.Walk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 반려동물·회원은 다른 도메인이므로 엔티티로 잇지 않고 id만 저장한다
@Entity
@Table(name = "walks",
		indexes = @Index(name = WalkJpaEntity.LOOKUP_INDEX, columnList = "created_by, pet_id, started_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class WalkJpaEntity {

	// 날짜별 조회용 인덱스. 실행 계획은 데이터를 쌓아 확인한 뒤 바꾼다
	static final String LOOKUP_INDEX = "idx_walks_lookup";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "pet_id", nullable = false)
	private Long petId;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "last_activity_at", nullable = false)
	private Instant lastActivityAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	static WalkJpaEntity from(Walk walk) {
		WalkJpaEntity entity = new WalkJpaEntity();
		entity.petId = walk.getPetId();
		entity.createdBy = walk.getCreatedBy();
		entity.startedAt = walk.getStartedAt();
		entity.updateState(walk);
		return entity;
	}

	// 끝낸 시각과 마지막 활동 시각만 바꾼다. 반려동물·작성자·시작 시각은 바뀌지 않는다.
	// 이미 정해진 끝낸 시각은 바꾸지 않는다. 잠그지 않고 읽은 값으로 저장하다가 먼저 끝낸 시각을 덮는 일을 막는 마지막 안전장치다
	void updateState(Walk walk) {
		if (this.endedAt != null && !this.endedAt.equals(walk.getEndedAt())) {
			throw new IllegalStateException("이미 끝난 산책의 끝낸 시각은 바꿀 수 없습니다. id=" + id);
		}
		this.endedAt = walk.getEndedAt();
		this.lastActivityAt = walk.getLastActivityAt();
	}

	// 반려동물·작성자·시작 시각이 모두 같은지 비교한다. 같은 산책 행인지 확인하는 용도다
	boolean hasSameIdentity(Walk walk) {
		return petId.equals(walk.getPetId()) && createdBy.equals(walk.getCreatedBy())
				&& startedAt.equals(walk.getStartedAt());
	}

	Walk toDomain() {
		return Walk.restore(id, petId, createdBy, startedAt, endedAt, lastActivityAt);
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

}
