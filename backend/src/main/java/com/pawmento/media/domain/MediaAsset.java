package com.pawmento.media.domain;

import static com.pawmento.media.domain.MediaViolation.Reason.*;

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
      throw new MediaViolation(INVALID, "회원과 반려동물 정보가 필요합니다.");
    if (filename == null
        || filename.isBlank()
        || filename.length() > 255
        || filename.indexOf('/') >= 0
        || filename.indexOf('\\') >= 0
        || filename.chars().anyMatch(Character::isISOControl))
      throw new MediaViolation(INVALID, "올바른 파일 이름을 입력해 주세요.");
    if (size <= 0) throw new MediaViolation(INVALID, "비어 있는 파일은 업로드할 수 없어요.");
    if (size > MAX_FILE_BYTES) throw new MediaViolation(TOO_LARGE, "파일은 15MiB까지 업로드할 수 있어요.");
    String normalized = mime == null ? "" : mime.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    MediaType type;
    if (IMAGES.contains(normalized)) type = MediaType.IMAGE;
    else if (AUDIO.contains(normalized)) type = MediaType.AUDIO;
    else throw new MediaViolation(UNSUPPORTED, "사진과 음성 녹음 파일만 업로드할 수 있어요.");
    MediaPurpose target;
    try {
      target = MediaPurpose.valueOf(purpose);
    } catch (RuntimeException e) {
      throw new MediaViolation(INVALID, "첨부 용도를 확인해 주세요.");
    }
    if (key == null || !key.matches("[0-9a-f-]{36}"))
      throw new MediaViolation(INVALID, "파일 저장 키가 올바르지 않아요.");
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
      throw new MediaViolation(FORBIDDEN, "다른 반려동물의 파일에 접근할 수 없어요.");
    if (!uploaderMemberId.equals(memberId))
      throw new MediaViolation(FORBIDDEN, "본인이 올린 파일만 변경할 수 있어요.");
  }

  public void requirePending(Instant now) {
    if (status != MediaUploadStatus.PENDING)
      throw new MediaViolation(CONFLICT, "이미 파일을 받았거나 처리 중인 업로드예요.");
    if (!now.isBefore(expiresAt))
      throw new MediaViolation(CONFLICT, "업로드 시간이 지났어요. 새로 파일을 선택해 주세요.");
  }

  public void begin(Instant now) {
    requirePending(now);
    status = MediaUploadStatus.UPLOADING;
    updatedAt = now;
  }

  public void received(StoredUpload upload, Instant now) {
    if (status != MediaUploadStatus.UPLOADING)
      throw new MediaViolation(CONFLICT, "파일을 받을 수 없는 상태예요.");
    if (upload.sizeBytes() != declaredSizeBytes)
      throw new MediaViolation(INVALID, "요청한 크기와 실제 파일 크기가 달라요.");
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
      throw new MediaViolation(CONFLICT, "파일 정리 상태를 확인해 주세요.");
    temporaryFileDeleted = true;
    updatedAt = now;
  }

  public void validated(VerifiedMedia result, Instant now) {
    if (status != MediaUploadStatus.PROCESSING)
      throw new MediaViolation(CONFLICT, "검증 중인 파일이 아니에요.");
    if ((mediaType == MediaType.IMAGE) != result.mimeType().startsWith("image/"))
      throw new MediaViolation(CONFLICT, "검증한 파일의 종류가 달라요.");
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
      throw new MediaViolation(CONFLICT, "미리보기를 할 수 없는 파일이에요.");
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
    if (referenceCount != 0) throw new MediaViolation(CONFLICT, "글에 연결된 파일은 삭제할 수 없어요.");
    if (status == MediaUploadStatus.DELETING || status == MediaUploadStatus.DELETED) return;
    status = MediaUploadStatus.DELETING;
    updatedAt = now;
  }

  public void deleted(Instant now) {
    if (status != MediaUploadStatus.DELETING) throw new MediaViolation(CONFLICT, "삭제 중인 파일이 아니에요.");
    status = MediaUploadStatus.DELETED;
    temporaryFileDeleted = true;
    deletedAt = now;
    updatedAt = now;
  }
}
