package com.pawmento.diary.adapter.out.persistence;

import com.pawmento.diary.application.exception.StoredDiaryTitleInvalidException;
import com.pawmento.diary.domain.DiaryEntry;
import com.pawmento.diary.domain.DiaryLocationIntent;
import com.pawmento.diary.domain.DiaryTitle;
import com.pawmento.diary.domain.exception.InvalidDiaryTitleException;
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

  // Hibernate는 기존 행에 nullable 열을 먼저 추가하고 시작 시 보강 뒤 NOT NULL을 적용한다.
  @Column(length = 255, updatable = false)
  private String authorNameSnapshot;

  @Column(nullable = false, length = 3000)
  private String content;

  @Column(length = 100)
  private String title;

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
    row.authorNameSnapshot = e.getAuthorName().value();
    row.content = e.getContent();
    row.title = e.getTitle().value();
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
          id,
          petId,
          createdBy,
          content,
          recordedDate,
          createdAt,
          updatedAt,
          version,
          intent,
          new com.pawmento.diary.domain.DiaryAuthorName(authorNameSnapshot),
          new DiaryTitle(title));
    } catch (InvalidDiaryTitleException failure) {
      throw new StoredDiaryTitleInvalidException(failure);
    } catch (com.pawmento.diary.domain.exception.InvalidDiaryLocationIntentException failure) {
      throw new com.pawmento.diary.application.exception.DiaryLocationDataInvalidException(
          "저장된 일기 위치 원본이 올바르지 않습니다.", failure);
    } catch (com.pawmento.diary.domain.exception.InvalidDiaryAuthorNameException failure) {
      // 저장된 이름을 복원하는 이 경로에서만 "저장된 데이터" 예외로 바꾼다.
      throw new com.pawmento.diary.application.exception.StoredDiaryAuthorInvalidException(failure);
    }
  }
}
