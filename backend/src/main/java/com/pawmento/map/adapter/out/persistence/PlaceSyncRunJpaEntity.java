package com.pawmento.map.adapter.out.persistence;

import java.time.Instant;

import com.pawmento.map.domain.PlaceCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 종류마다 마지막으로 가져오기를 반영한 시각. 장소의 updated_at은 바뀐 행만 갱신돼서 "마지막으로 가져온 시각"이 아니라서 따로 둔다.
// 지도 화면의 "데이터 기준일"에 쓴다(#172-S21)
@Entity
@Table(name = "place_sync_runs")
class PlaceSyncRunJpaEntity {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private PlaceCategory category;

	@Column(nullable = false)
	private Instant lastSyncedAt;

	protected PlaceSyncRunJpaEntity() {
	}

	PlaceSyncRunJpaEntity(PlaceCategory category, Instant lastSyncedAt) {
		this.category = category;
		this.lastSyncedAt = lastSyncedAt;
	}

	void syncedAt(Instant at) {
		this.lastSyncedAt = at;
	}

	PlaceCategory getCategory() {
		return category;
	}

	Instant getLastSyncedAt() {
		return lastSyncedAt;
	}

}
