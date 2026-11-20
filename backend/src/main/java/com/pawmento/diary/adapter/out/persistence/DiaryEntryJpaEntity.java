package com.pawmento.diary.adapter.out.persistence;

import com.pawmento.diary.domain.DiaryEntry;
import com.pawmento.diary.domain.DiaryLocationIntent;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity
@Table(name = "diary_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryEntryJpaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long petId;

  @Column(nullable = false)
  private Long createdBy;

  @Column(nullable = false, length = 3000)
  private String content;

  @Column(nullable = false)
  private LocalDate recordedDate;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private DiaryLocationIntent.Mode locationMode;

  @Column(columnDefinition = "double precision")
  private Double locationLatitude;

  @Column(columnDefinition = "double precision")
  private Double locationLongitude;

  @Column(length = 255)
  private String locationPlaceName;

  static DiaryEntryJpaEntity from(DiaryEntry e) {
    var row = new DiaryEntryJpaEntity();
    row.id = e.getId();
    row.petId = e.getPetId();
    row.createdBy = e.getCreatedBy();
    row.content = e.getContent();
    row.recordedDate = e.getRecordedDate();
    row.createdAt = e.getCreatedAt();
    row.updatedAt = e.getUpdatedAt();
    row.version = e.getVersion();
    var location = e.getLocationIntent();
    row.locationMode = location == null ? null : location.mode();
    row.locationLatitude = location == null ? null : location.latitude();
    row.locationLongitude = location == null ? null : location.longitude();
    row.locationPlaceName = location == null ? null : location.placeName();
    return row;
  }

  DiaryEntry toDomain() {
    try {
      if (locationMode == null
          && (locationLatitude != null || locationLongitude != null || locationPlaceName != null))
        throw new com.pawmento.diary.application.exception.DiaryLocationDataInvalidException(
            "위치 모드 없이 저장된 좌표가 있습니다.");
      var intent =
          locationMode == null
              ? null
              : new DiaryLocationIntent(
                  locationMode, locationLatitude, locationLongitude, locationPlaceName);
      return DiaryEntry.restore(
          id, petId, createdBy, content, recordedDate, createdAt, updatedAt, version, intent);
    } catch (com.pawmento.diary.domain.exception.InvalidDiaryLocationIntentException failure) {
      throw new com.pawmento.diary.application.exception.DiaryLocationDataInvalidException(
          "저장된 일기 위치 원본이 올바르지 않습니다.", failure);
    }
  }
}
