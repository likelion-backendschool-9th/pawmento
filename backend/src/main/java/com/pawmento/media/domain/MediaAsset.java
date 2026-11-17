package com.pawmento.media.domain;

import com.pawmento.media.domain.exception.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import lombok.Getter;

@Getter
public class MediaAsset {
  public static final long MAX_FILE_BYTES = 15L * 1024 * 1024;
  public static final int MAX_OPEN_UPLOADS = 5;
  public static final Duration SESSION_TTL = Duration.ofMinutes(10);
  private static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp");
  private static final Set<String> AUDIO =
      Set.of("audio/webm", "audio/ogg", "audio/mp4", "audio/mpeg");
  private Long id;
  private Long uploaderMemberId;
  private Long petId;
  private MediaPurpose purpose;
  private MediaType mediaType;
  private String originalFilename;
  private String declaredMimeType;
  private long declaredSizeBytes;
  private String temporaryStorageKey;
  private MediaUploadStatus status;
  private Instant createdAt;
  private Instant expiresAt;
  private Instant updatedAt;
  private Long receivedSizeBytes;
  private String sha256;
  private String failureReason;
  private boolean temporaryFileDeleted;
  private Long version;
  private VerifiedMedia verified;
  private Instant readyAt;
  private Instant unattachedExpiresAt;
  private Instant deletedAt;
  private int referenceCount;
  public static final Duration UNATTACHED_TTL = Duration.ofHours(24);
  public static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(15);

  private MediaAsset() {}

  public static MediaAsset create(
      Long memberId,
      Long petId,
      String purpose,
      String filename,
      String mime,
      long size,
      String key,
      Instant now) {
    if (memberId == null || memberId <= 0 || petId == null || petId <= 0)
      throw new InvalidMediaException(InvalidMediaException.Reason.IDENTITY_REQUIRED);
    if (filename == null
        || filename.isBlank()
        || filename.length() > 255
        || filename.indexOf('/') >= 0
        || filename.indexOf('\\') >= 0
        || filename.chars().anyMatch(Character::isISOControl))
      throw new InvalidMediaException(InvalidMediaException.Reason.INVALID_FILENAME);
    if (size <= 0) throw new InvalidMediaException(InvalidMediaException.Reason.EMPTY_FILE);
    if (size > MAX_FILE_BYTES) throw new MediaSizeLimitExceededException();
    String normalized = mime == null ? "" : mime.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    MediaType type;
    if (IMAGES.contains(normalized)) type = MediaType.IMAGE;
    else if (AUDIO.contains(normalized)) type = MediaType.AUDIO;
    else throw new UnsupportedMediaFormatException();
    MediaPurpose target;
    try {
      target = MediaPurpose.valueOf(purpose);
    } catch (RuntimeException e) {
      throw new InvalidMediaException(InvalidMediaException.Reason.INVALID_PURPOSE);
    }
    if (key == null || !key.matches("[0-9a-f-]{36}"))
      throw new InvalidMediaException(InvalidMediaException.Reason.INVALID_STORAGE_KEY);
    MediaAsset a = new MediaAsset();
    a.uploaderMemberId = memberId;
    a.petId = petId;
    a.purpose = target;
    a.mediaType = type;
    a.originalFilename = filename.strip();
    a.declaredMimeType = normalized;
    a.declaredSizeBytes = size;
    a.temporaryStorageKey = key;
    a.status = MediaUploadStatus.PENDING;
    a.createdAt = now;
    a.updatedAt = now;
    a.expiresAt = now.plus(SESSION_TTL);
    return a;
  }

  public static MediaAsset restore(
      Long id,
      Long memberId,
      Long petId,
      MediaPurpose purpose,
      MediaType type,
      String filename,
      String mime,
      long size,
      String key,
      MediaUploadStatus status,
      Instant created,
      Instant expires,
      Instant updated,
      Long received,
      String checksum,
      String failure,
      boolean cleaned,
      Long version,
      VerifiedMedia verified,
      Instant readyAt,
      Instant unattachedExpiresAt,
      Instant deletedAt,
      int referenceCount) {
    MediaAsset a = new MediaAsset();
    a.id = id;
    a.uploaderMemberId = memberId;
    a.petId = petId;
    a.purpose = purpose;
    a.mediaType = type;
    a.originalFilename = filename;
    a.declaredMimeType = mime;
    a.declaredSizeBytes = size;
    a.temporaryStorageKey = key;
    a.status = status;
    a.createdAt = created;
    a.expiresAt = expires;
    a.updatedAt = updated;
    a.receivedSizeBytes = received;
    a.sha256 = checksum;
    a.failureReason = failure;
    a.temporaryFileDeleted = cleaned;
    a.version = version;
    a.verified = verified;
    a.readyAt = readyAt;
    a.unattachedExpiresAt = unattachedExpiresAt;
    a.deletedAt = deletedAt;
    a.referenceCount = referenceCount;
    return a;
  }

  public void requireOwner(Long memberId, Long requestedPetId) {
    if (!petId.equals(requestedPetId))
      throw new MediaAccessDeniedException(MediaAccessDeniedException.Reason.WRONG_PET);
    if (!uploaderMemberId.equals(memberId))
      throw new MediaAccessDeniedException(MediaAccessDeniedException.Reason.NOT_UPLOADER);
  }

  public void requirePending(Instant now) {
    if (status != MediaUploadStatus.PENDING)
      throw new MediaStateConflictException(MediaStateConflictException.Reason.ALREADY_RECEIVED);
    if (!now.isBefore(expiresAt))
      throw new MediaStateConflictException(MediaStateConflictException.Reason.SESSION_EXPIRED);
  }

  public void begin(Instant now) {
    requirePending(now);
    status = MediaUploadStatus.UPLOADING;
    updatedAt = now;
  }

  public void received(StoredUpload upload, Instant now) {
    if (status != MediaUploadStatus.UPLOADING)
      throw new MediaStateConflictException(MediaStateConflictException.Reason.NOT_UPLOADING);
    if (upload.sizeBytes() != declaredSizeBytes)
      throw new InvalidMediaException(InvalidMediaException.Reason.SIZE_MISMATCH);
    receivedSizeBytes = upload.sizeBytes();
    sha256 = upload.sha256();
    status = MediaUploadStatus.PROCESSING;
    updatedAt = now;
  }

  public boolean rejectUpload(Instant now) {
    if (status != MediaUploadStatus.UPLOADING) return false;
    status = MediaUploadStatus.REJECTED;
    failureReason = "UPLOAD_FAILED";
    updatedAt = now;
    return true;
  }

  public void temporaryRemoved(Instant now) {
    if (status != MediaUploadStatus.REJECTED
        && status != MediaUploadStatus.READY
        && status != MediaUploadStatus.DELETING
        && status != MediaUploadStatus.DELETED)
      throw new MediaStateConflictException(
          MediaStateConflictException.Reason.INVALID_CLEANUP_STATE);
    temporaryFileDeleted = true;
    updatedAt = now;
  }

  public void validated(VerifiedMedia result, Instant now) {
    if (status != MediaUploadStatus.PROCESSING)
      throw new MediaStateConflictException(MediaStateConflictException.Reason.NOT_PROCESSING);
    if ((mediaType == MediaType.IMAGE) != result.mimeType().startsWith("image/"))
      throw new MediaStateConflictException(MediaStateConflictException.Reason.TYPE_CHANGED);
    verified = result;
    readyAt = now;
    unattachedExpiresAt = now.plus(UNATTACHED_TTL);
    status = MediaUploadStatus.READY;
    failureReason = null;
    updatedAt = now;
  }

  public void invalidMedia(String reason, Instant now) {
    if (status != MediaUploadStatus.PROCESSING) return;
    status = MediaUploadStatus.REJECTED;
    failureReason = reason;
    updatedAt = now;
  }

  public void requirePreview(Instant now) {
    if (status != MediaUploadStatus.READY
        || referenceCount != 0
        || !now.isBefore(unattachedExpiresAt))
      throw new MediaStateConflictException(MediaStateConflictException.Reason.PREVIEW_UNAVAILABLE);
  }

  public boolean needsCleanup(Instant now) {
    return switch (status) {
      case PENDING -> !now.isBefore(expiresAt);
      case UPLOADING -> !now.isBefore(updatedAt.plus(UPLOAD_TIMEOUT));
      case READY -> referenceCount == 0 && !now.isBefore(unattachedExpiresAt);
      case REJECTED -> true;
      default -> false;
    };
  }

  public void requestDeletion(Instant now) {
    if (referenceCount != 0)
      throw new MediaStateConflictException(MediaStateConflictException.Reason.FILE_REFERENCED);
    if (status == MediaUploadStatus.DELETING || status == MediaUploadStatus.DELETED) return;
    status = MediaUploadStatus.DELETING;
    updatedAt = now;
  }

  public void deleted(Instant now) {
    if (status != MediaUploadStatus.DELETING)
      throw new MediaStateConflictException(MediaStateConflictException.Reason.NOT_DELETING);
    status = MediaUploadStatus.DELETED;
    temporaryFileDeleted = true;
    deletedAt = now;
    updatedAt = now;
  }
}
