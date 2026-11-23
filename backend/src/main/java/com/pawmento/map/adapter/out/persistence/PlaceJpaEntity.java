package com.pawmento.map.adapter.out.persistence;

import java.time.Instant;

import com.pawmento.map.domain.Open24hBasis;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;

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

// 공공데이터에서 가져온 장소. 같은 종류 안에서 원본의 관리번호(source_id)가 하나뿐이도록 지킨다
@Entity
@Table(name = "places",
		uniqueConstraints = @UniqueConstraint(name = "uk_places_category_source", columnNames = { "category", "source_id" }),
		indexes = @Index(name = "idx_places_area", columnList = "category, latitude, longitude"))
class PlaceJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlaceCategory category;

	// 원본의 관리번호(병원·미용은 MNG_NO, 보호센터는 careRegNo)
	@Column(nullable = false, length = 100)
	private String sourceId;

	@Column(nullable = false)
	private String name;

	private String address;

	@Column(length = 30)
	private String phone;

	@Column(nullable = false)
	private double latitude;

	@Column(nullable = false)
	private double longitude;

	@Column(nullable = false)
	private boolean open24h;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private Open24hBasis open24hBasis;

	private String openingHours;

	// null은 휴무 정보 없음이다
	private String closedDays;

	// 원본이 마지막으로 바뀐 시각(알 수 없으면 null)
	private Instant sourceUpdatedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected PlaceJpaEntity() {
	}

	Place toDomain() {
		return new Place(id, category, name, address, phone, latitude, longitude, open24h, open24hBasis, openingHours,
				closedDays);
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
