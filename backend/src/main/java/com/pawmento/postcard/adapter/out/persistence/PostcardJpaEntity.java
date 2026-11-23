package com.pawmento.postcard.adapter.out.persistence;

import com.pawmento.postcard.domain.Postcard;
import com.pawmento.postcard.domain.PostcardSourceKind;
import com.pawmento.postcard.domain.PostcardStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(name = "postcards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostcardJpaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long petId;

  @Column(nullable = false)
  private Long ownerMemberId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PostcardSourceKind sourceKind;

  @Column(nullable = false)
  private Long sourceId;

  @Column(nullable = false, length = Postcard.MAX_CONTENT)
  private String sourceContent;

  @Column(length = Postcard.MAX_NOTE)
  private String sourceNote;

  @Column(nullable = false, length = 16)
  private String sourcePetSpecies;

  @Column(length = 100)
  private String sourcePetBreed;

  @Column(length = 64)
  private String sourcePhotoKey;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(nullable = false, length = Postcard.MAX_NOTE)
  private String text;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PostcardStatus status;

  @Column(length = 200)
  private String errorMessage;

  @Column(length = 64)
  private String imageKey;

  @Column(nullable = false)
  private LocalDate requestedOn;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant processingStartedAt;

  private Instant completedAt;

  static PostcardJpaEntity from(Postcard p) {
    var row = new PostcardJpaEntity();
    row.id = p.getId();
    row.petId = p.getPetId();
    row.ownerMemberId = p.getOwnerMemberId();
    row.sourceKind = p.getSourceKind();
    row.sourceId = p.getSourceId();
    row.sourceContent = p.getSourceContent();
    row.sourceNote = p.getSourceNote();
    row.sourcePetSpecies = p.getSourcePetSpecies();
    row.sourcePetBreed = p.getSourcePetBreed();
    row.sourcePhotoKey = p.getSourcePhotoKey();
    row.title = p.getTitle();
    row.text = p.getText();
    row.status = p.getStatus();
    row.errorMessage = p.getErrorMessage();
    row.imageKey = p.getImageKey();
    row.requestedOn = p.getRequestedOn();
    row.createdAt = p.getCreatedAt();
    row.processingStartedAt = p.getProcessingStartedAt();
    row.completedAt = p.getCompletedAt();
    return row;
  }

  Postcard toDomain() {
    return Postcard.restore(
        id,
        petId,
        ownerMemberId,
        sourceKind,
        sourceId,
        sourceContent,
        sourceNote,
        sourcePetSpecies,
        sourcePetBreed,
        sourcePhotoKey,
        title,
        text,
        status,
        errorMessage,
        imageKey,
        requestedOn,
        createdAt,
        processingStartedAt,
        completedAt);
  }
}
