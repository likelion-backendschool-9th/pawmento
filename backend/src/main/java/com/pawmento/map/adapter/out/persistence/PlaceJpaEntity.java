package com.pawmento.map.adapter.out.persistence;

import java.time.Instant;
import java.util.Objects;

import com.pawmento.map.domain.Open24hBasis;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;

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

	// 검색용으로 정규화한 이름·주소(#195). 영역 조회의 겹침 제거와 같은 기준(PlaceSelection.comparableName)이다.
	// 이 열을 더하기 전에 저장한 행은 비어 있고, 서버가 시작할 때 PlaceSearchKeyBackfill이 채운다
	private String nameKey;

	private String addressKey;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected PlaceJpaEntity() {
	}

	// 공공데이터에서 가져온 장소로 새 행을 만든다
	static PlaceJpaEntity from(PlaceDraft draft) {
		PlaceJpaEntity entity = new PlaceJpaEntity();
		entity.category = draft.category();
		entity.sourceId = draft.sourceId();
		entity.apply(draft);
		return entity;
	}

	/**
	 * 가져온 값으로 바꾼다. 하나라도 달라졌으면 true를 돌려주고, 같으면 아무것도 바꾸지 않아 수정 시각이 그대로다.
	 * 종류와 관리번호는 행을 찾는 기준이라 바꾸지 않는다.
	 */
	boolean apply(PlaceDraft draft) {
		boolean changed = !Objects.equals(name, draft.name()) || !Objects.equals(address, draft.address())
				|| !Objects.equals(phone, draft.phone()) || latitude != draft.latitude() || longitude != draft.longitude()
				|| open24h != draft.open24h() || open24hBasis != draft.open24hBasis()
				|| !Objects.equals(openingHours, draft.openingHours()) || !Objects.equals(closedDays, draft.closedDays())
				|| !Objects.equals(sourceUpdatedAt, draft.sourceUpdatedAt());
		if (changed) {
			this.name = draft.name();
			this.address = draft.address();
			this.phone = draft.phone();
			this.latitude = draft.latitude();
			this.longitude = draft.longitude();
			this.open24h = draft.open24h();
			this.open24hBasis = draft.open24hBasis();
			this.openingHours = draft.openingHours();
			this.closedDays = draft.closedDays();
			this.sourceUpdatedAt = draft.sourceUpdatedAt();
			this.nameKey = draft.nameKey();
			this.addressKey = draft.addressKey();
		}
		return changed;
	}

	// 검색용 열이 비어 있는 이전 행인지(#195-S19)
	boolean lacksSearchKeys() {
		return nameKey == null;
	}

	// 이름·주소에서 검색용 열을 채운다. 가져오기의 갱신 건수(apply)에는 세지 않는다. 장소의 내용이 바뀐 것이 아니다
	void fillSearchKeys() {
		Place place = toDomain();
		this.nameKey = place.nameKey();
		this.addressKey = place.addressKey();
	}

	String getSourceId() {
		return sourceId;
	}

	Long getId() {
		return id;
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
