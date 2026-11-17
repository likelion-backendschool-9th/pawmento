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

  @Column(name = "uploader_member_id", nullable = false)
  private Long uploaderMemberId;

  @Column(name = "pet_id", nullable = false)
  private Long petId;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false, length = 40)
  private MediaPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(name = "media_type", nullable = false, length = 16)
  private MediaType mediaType;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "declared_mime_type", nullable = false, length = 100)
  private String declaredMimeType;

  @Column(name = "declared_size_bytes", nullable = false)
  private long declaredSizeBytes;

  @Column(name = "temporary_storage_key", nullable = false, length = 36, unique = true)
  private String temporaryStorageKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private MediaUploadStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "received_size_bytes")
  private Long receivedSizeBytes;

  @Column(name = "sha256", length = 64)
  private String sha256;

  @Column(name = "failure_reason", length = 100)
  private String failureReason;

  @Column(name = "temporary_file_deleted", nullable = false)
  private boolean temporaryFileDeleted;

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
        version);
  }
}
