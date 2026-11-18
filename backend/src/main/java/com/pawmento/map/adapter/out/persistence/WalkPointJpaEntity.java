package com.pawmento.map.adapter.out.persistence;

import java.time.Instant;

import com.pawmento.map.domain.WalkPoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 좌표는 산책 id만 저장한다. (walk_id, recorded_at) 유니크 제약이 재전송 중복을 막고 시간순 조회의 인덱스도 겸한다.
// 좌표를 쌓는 일은 이 엔티티가 아니라 WalkPointJpaRepository의 insertIfAbsent가 한다
@Entity
@Table(name = "walk_points", uniqueConstraints = @UniqueConstraint(name = WalkPointJpaEntity.WALK_RECORDED_AT_UNIQUE,
		columnNames = { "walk_id", "recorded_at" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class WalkPointJpaEntity {

	static final String WALK_RECORDED_AT_UNIQUE = "uk_walk_points_walk_recorded_at";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "walk_id", nullable = false)
	private Long walkId;

	@Column(nullable = false)
	private double latitude;

	@Column(nullable = false)
	private double longitude;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	WalkPoint toDomain() {
		return WalkPoint.of(walkId, latitude, longitude, recordedAt);
	}

}
