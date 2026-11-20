package com.pawmento.map.domain;

import java.util.Objects;

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

	// 위치의 출처와, 이 위치를 마지막으로 반영한 revision(다이어리가 확정한 버전). 사용자가 직접 고른 위치는 자산이 없다
	private final LocationSource source;
	private final Long sourceAssetId;
	private final long sourceRevision;

	private DiaryLocation(Long id, Long diaryId, Long petId, Long createdBy, double latitude, double longitude,
			String placeName, LocationSource source, Long sourceAssetId, long sourceRevision) {
		if (diaryId == null || petId == null || createdBy == null) {
			throw new IllegalArgumentException("기록, 반려동물, 작성자가 필요합니다.");
		}
		if (source == null) {
			throw new IllegalArgumentException("위치의 출처가 필요합니다.");
		}
		if (source == LocationSource.MANUAL && sourceAssetId != null) {
			throw new IllegalArgumentException("직접 고른 위치에는 출처 자산이 없어야 합니다.");
		}
		if (sourceRevision < 0) {
			throw new IllegalArgumentException("revision은 0 이상이어야 합니다.");
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
		this.source = source;
		this.sourceAssetId = sourceAssetId;
		this.sourceRevision = sourceRevision;
	}

	// 새로 붙이는 위치. id는 DB에 저장한 뒤 생긴다. 사용자가 직접 고른 위치이고 revision은 0에서 시작한다
	public static DiaryLocation create(Long diaryId, Long petId, Long createdBy, double latitude, double longitude,
			String placeName) {
		return new DiaryLocation(null, diaryId, petId, createdBy, latitude, longitude, placeName,
				LocationSource.MANUAL, null, 0);
	}

	// 저장된 값으로 다시 만든다. 새로 만들 때와 같은 규칙을 검사해, 잘못된 값이 들어 있으면 바로 드러나게 한다
	public static DiaryLocation restore(Long id, Long diaryId, Long petId, Long createdBy, double latitude,
			double longitude, String placeName, LocationSource source, Long sourceAssetId, long sourceRevision) {
		if (id == null) {
			throw new IllegalArgumentException("저장된 위치에는 id가 필요합니다.");
		}
		return new DiaryLocation(id, diaryId, petId, createdBy, latitude, longitude, placeName, source,
				sourceAssetId, sourceRevision);
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
	public static String normalizePlaceName(String placeName) {
		if (placeName == null || placeName.isBlank()) {
			return null;
		}
		String trimmed = placeName.strip();
		if (trimmed.length() > PLACE_NAME_MAX_LENGTH) {
			throw InvalidDiaryLocationException.placeNameTooLong(PLACE_NAME_MAX_LENGTH);
		}
		return trimmed;
	}

	public enum ApplyDecision {
		APPLIED,
		DUPLICATE,
		STALE,
		CONTRACT_VIOLATION
	}

	/** 일기가 보장한 식별자·revision과 현재 핀을 비교한다. 정정 경로는 별도 관측값으로 검사한다. */
	public static ApplyDecision assess(
			DiaryLocationState current,
			Long petId,
			Long createdBy,
			long revision,
			DiaryLocationValue requested) {
		if (current == null) {
			return requested == null ? ApplyDecision.DUPLICATE : ApplyDecision.APPLIED;
		}
		if (!Objects.equals(current.petId(), petId)
				|| !Objects.equals(current.createdBy(), createdBy)) {
			return ApplyDecision.CONTRACT_VIOLATION;
		}
		if (revision < current.revision()) {
			return ApplyDecision.STALE;
		}
		if (revision == current.revision()) {
			if (requested != null
					&& current.source() == LocationSource.MANUAL
					&& current.sourceAssetId() == null
					&& Double.compare(current.latitude(), requested.latitude()) == 0
					&& Double.compare(current.longitude(), requested.longitude()) == 0
					&& Objects.equals(
							normalizePlaceName(current.placeName()), requested.placeName())) {
				return ApplyDecision.DUPLICATE;
			}
			return ApplyDecision.CONTRACT_VIOLATION;
		}
		return ApplyDecision.APPLIED;
	}

	/** 정정은 revision을 덮어쓸 수 있지만, 관측 이후 바뀐 핀을 덮어쓰면 안 된다. */
	public static ApplyDecision assessReconciliation(DiaryLocationState current, DiaryLocationState expected,
			DiaryLocationValue requested) {
		if (!Objects.equals(current, expected)) {
			return ApplyDecision.STALE;
		}
		return current == null && requested == null ? ApplyDecision.DUPLICATE : ApplyDecision.APPLIED;
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

	public LocationSource getSource() {
		return source;
	}

	public Long getSourceAssetId() {
		return sourceAssetId;
	}

	public long getSourceRevision() {
		return sourceRevision;
	}

}
