package com.pawmento.map.domain;

import com.pawmento.map.domain.exception.InvalidDiaryLocationException;

/**
 * 기록 하나에 붙은 위치. 기록 하나에는 위치 하나만 붙는다.
 * 다른 도메인의 엔티티를 참조하지 않고 기록·반려동물·회원의 id만 가진다.
 */
public class DiaryLocation {

	public static final int PLACE_NAME_MAX_LENGTH = 255;

	private final Long id;
	private final Long diaryId;
	private final Long petId;
	private final Long createdBy;
	private double latitude;
	private double longitude;
	private String placeName;

	private DiaryLocation(Long id, Long diaryId, Long petId, Long createdBy, double latitude, double longitude,
			String placeName) {
		if (diaryId == null || petId == null || createdBy == null) {
			throw new IllegalArgumentException("기록, 반려동물, 작성자가 필요합니다.");
		}
		Coordinates.requireLatitude(latitude);
		Coordinates.requireLongitude(longitude);
		this.id = id;
		this.diaryId = diaryId;
		this.petId = petId;
		this.createdBy = createdBy;
		this.latitude = latitude;
		this.longitude = longitude;
		this.placeName = normalizePlaceName(placeName);
	}

	// 새로 붙이는 위치. id는 DB에 저장한 뒤 생긴다
	public static DiaryLocation create(Long diaryId, Long petId, Long createdBy, double latitude, double longitude,
			String placeName) {
		return new DiaryLocation(null, diaryId, petId, createdBy, latitude, longitude, placeName);
	}

	// 저장된 값으로 다시 만든다. 새로 만들 때와 같은 규칙을 검사해, 잘못된 값이 들어 있으면 바로 드러나게 한다
	public static DiaryLocation restore(Long id, Long diaryId, Long petId, Long createdBy, double latitude,
			double longitude, String placeName) {
		if (id == null) {
			throw new IllegalArgumentException("저장된 위치에는 id가 필요합니다.");
		}
		return new DiaryLocation(id, diaryId, petId, createdBy, latitude, longitude, placeName);
	}

	// 같은 기록의 위치를 바꾼다. 새 값도 같은 규칙을 지켜야 하고, 하나라도 어긋나면 아무것도 바꾸지 않는다
	public void move(double latitude, double longitude, String placeName) {
		Coordinates.requireLatitude(latitude);
		Coordinates.requireLongitude(longitude);
		String normalized = normalizePlaceName(placeName);
		this.latitude = latitude;
		this.longitude = longitude;
		this.placeName = normalized;
	}

	// 비었거나 공백뿐이면 장소 이름이 없는 것으로 본다
	private static String normalizePlaceName(String placeName) {
		if (placeName == null || placeName.isBlank()) {
			return null;
		}
		String trimmed = placeName.strip();
		if (trimmed.length() > PLACE_NAME_MAX_LENGTH) {
			throw InvalidDiaryLocationException.placeNameTooLong(PLACE_NAME_MAX_LENGTH);
		}
		return trimmed;
	}

	public Long getId() {
		return id;
	}

	public Long getDiaryId() {
		return diaryId;
	}

	public Long getPetId() {
		return petId;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public String getPlaceName() {
		return placeName;
	}

}
