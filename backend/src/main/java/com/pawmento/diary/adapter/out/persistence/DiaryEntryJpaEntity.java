package com.pawmento.diary.adapter.out.persistence;

import com.pawmento.diary.domain.DiaryEntry;
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

  @Column(name = "pet_id", nullable = false)
  private Long petId;

  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  @Column(nullable = false, length = 3000)
  private String content;

  @Column(name = "recorded_date", nullable = false)
  private LocalDate recordedDate;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

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
    return row;
  }

  DiaryEntry toDomain() {
    return DiaryEntry.restore(
        id, petId, createdBy, content, recordedDate, createdAt, updatedAt, version);
  }
}
