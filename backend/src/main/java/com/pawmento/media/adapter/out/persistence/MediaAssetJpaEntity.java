package com.pawmento.media.adapter.out.persistence;

import com.pawmento.media.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "media_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaAssetJpaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long uploaderMemberId;

  @Column(nullable = false)
  private Long petId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private MediaPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private MediaType mediaType;

  @Column(nullable = false, length = 255)
  private String originalFilename;

  @Column(nullable = false, length = 100)
  private String declaredMimeType;

  @Column(nullable = false)
  private long declaredSizeBytes;

  @Column(nullable = false, length = 36, unique = true)
  private String temporaryStorageKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MediaUploadStatus status;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private Instant updatedAt;

  private Long receivedSizeBytes;

  @Column(length = 64)
  private String sha256;

  @Column(length = 100)
  private String failureReason;

  @Column(nullable = false)
  private boolean temporaryFileDeleted;

  @Embedded private VerifiedMediaColumns verified;
  private Instant readyAt;
  private Instant unattachedExpiresAt;
  private Instant deletedAt;

  @Column(nullable = false, columnDefinition = "integer default 0")
  private int referenceCount;

  @Version private Long version;

  public static MediaAssetJpaEntity from(MediaAsset a) {
    MediaAssetJpaEntity e = new MediaAssetJpaEntity();
    e.id = a.getId();
    e.uploaderMemberId = a.getUploaderMemberId();
    e.petId = a.getPetId();
    e.purpose = a.getPurpose();
    e.mediaType = a.getMediaType();
    e.originalFilename = a.getOriginalFilename();
    e.declaredMimeType = a.getDeclaredMimeType();
    e.declaredSizeBytes = a.getDeclaredSizeBytes();
    e.temporaryStorageKey = a.getTemporaryStorageKey();
    e.status = a.getStatus();
    e.createdAt = a.getCreatedAt();
    e.expiresAt = a.getExpiresAt();
    e.updatedAt = a.getUpdatedAt();
    e.receivedSizeBytes = a.getReceivedSizeBytes();
    e.sha256 = a.getSha256();
    e.failureReason = a.getFailureReason();
    e.temporaryFileDeleted = a.isTemporaryFileDeleted();
    e.version = a.getVersion();
    e.verified = VerifiedMediaColumns.from(a.getVerified());
    e.readyAt = a.getReadyAt();
    e.unattachedExpiresAt = a.getUnattachedExpiresAt();
    e.deletedAt = a.getDeletedAt();
    e.referenceCount = a.getReferenceCount();
    return e;
  }

  public MediaAsset toDomain() {
    return MediaAsset.restore(
        id,
        uploaderMemberId,
        petId,
        purpose,
        mediaType,
        originalFilename,
        declaredMimeType,
        declaredSizeBytes,
        temporaryStorageKey,
        status,
        createdAt,
        expiresAt,
        updatedAt,
        receivedSizeBytes,
        sha256,
        failureReason,
        temporaryFileDeleted,
        version,
        verified == null ? null : verified.toDomain(),
        readyAt,
        unattachedExpiresAt,
        deletedAt,
        referenceCount);
  }
}
