package com.pawmento.postcard.domain;

import com.pawmento.postcard.domain.exception.InvalidPostcardException;
import com.pawmento.postcard.domain.exception.PostcardConsentRequiredException;
import com.pawmento.postcard.domain.exception.PostcardMaterialMissingException;
import com.pawmento.postcard.domain.exception.PostcardModeNotAllowedException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.Getter;

// 요청 때 원본 내용을 복사해 두고, 생성 작업이 그 복사본으로 그림을 만든다.
@Getter
public class Postcard {
  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  public static final int MAX_CONTENT = 3000;
  public static final int MAX_NOTE = 200;
  public static final int TEXT_LENGTH = 200;

  private final Long id;
  private final Long petId;
  private final Long ownerMemberId;
  private final PostcardSourceKind sourceKind;
  private final Long sourceId;
  private final String sourceContent;
  private final String sourceNote;
  private final String sourcePetSpecies;
  private final String sourcePetBreed;
  private final String sourcePhotoKey;
  private final String title;
  private final String text;
  private final PostcardStatus status;
  private final String errorMessage;
  private final String imageKey;
  private final LocalDate requestedOn;
  private final Instant createdAt;
  private final Instant processingStartedAt;
  private final Instant completedAt;

  private Postcard(
      Long id,
      Long petId,
      Long ownerMemberId,
      PostcardSourceKind sourceKind,
      Long sourceId,
      String sourceContent,
      String sourceNote,
      String sourcePetSpecies,
      String sourcePetBreed,
      String sourcePhotoKey,
      String title,
      String text,
      PostcardStatus status,
      String errorMessage,
      String imageKey,
      LocalDate requestedOn,
      Instant createdAt,
      Instant processingStartedAt,
      Instant completedAt) {
    if (petId == null
        || ownerMemberId == null
        || sourceKind == null
        || sourceId == null
        || sourceContent == null
        || sourcePetSpecies == null
        || title == null
        || text == null
        || status == null
        || requestedOn == null
        || createdAt == null) throw new InvalidPostcardException("엽서 값이 비어 있어요.");
    if (sourceContent.length() > MAX_CONTENT)
      throw new InvalidPostcardException("일기 글이 너무 길어요.");
    if (sourceNote != null && sourceNote.length() > MAX_NOTE)
      throw new InvalidPostcardException("설명은 200자까지 쓸 수 있어요.");
    if ((status == PostcardStatus.COMPLETED) != (imageKey != null && completedAt != null))
      throw new InvalidPostcardException("완성된 엽서에만 이미지가 있어요.");
    if ((status == PostcardStatus.FAILED) != (errorMessage != null))
      throw new InvalidPostcardException("실패한 엽서에만 실패 안내가 있어요.");
    this.id = id;
    this.petId = petId;
    this.ownerMemberId = ownerMemberId;
    this.sourceKind = sourceKind;
    this.sourceId = sourceId;
    this.sourceContent = sourceContent;
    this.sourceNote = sourceNote;
    this.sourcePetSpecies = sourcePetSpecies;
    this.sourcePetBreed = sourcePetBreed;
    this.sourcePhotoKey = sourcePhotoKey;
    this.title = title;
    this.text = text;
    this.status = status;
    this.errorMessage = errorMessage;
    this.imageKey = imageKey;
    this.requestedOn = requestedOn;
    this.createdAt = createdAt;
    this.processingStartedAt = processingStartedAt;
    this.completedAt = completedAt;
  }

  // 정원 모드에서만 만든다. 글, 쓸 수 있는 사진, 추가 설명 중 하나는 있어야 그림을 만들 수 있다.
  // 복사한 사진 파일 이름은 엽서 ID로 정하므로 저장한 뒤에 붙인다.
  public static Postcard request(
      Long petId,
      Long ownerMemberId,
      String petName,
      PostcardSourceKind sourceKind,
      Long sourceId,
      String sourceContent,
      String note,
      String petSpecies,
      String petBreed,
      boolean garden,
      boolean hasPhoto,
      boolean consent,
      Instant now) {
    // 글과 사진을 외부 AI 서비스로 보내므로 웹이 아닌 경로도 동의 없이는 만들지 않는다.
    if (!consent) throw new PostcardConsentRequiredException();
    if (!garden) throw new PostcardModeNotAllowedException();
    String content = sourceContent == null ? "" : sourceContent;
    String cleanNote = note == null || note.isBlank() ? null : note.strip();
    if (content.isBlank() && !hasPhoto && cleanNote == null)
      throw new PostcardMaterialMissingException();
    String text = content.isBlank() ? (cleanNote == null ? "" : cleanNote) : head(content);
    return new Postcard(
        null,
        petId,
        ownerMemberId,
        sourceKind,
        sourceId,
        content,
        cleanNote,
        petSpecies,
        petBreed,
        null,
        title(petName),
        text,
        PostcardStatus.PROCESSING,
        null,
        null,
        LocalDate.ofInstant(now, ZONE),
        now,
        null,
        null);
  }

  public static Postcard restore(
      Long id,
      Long petId,
      Long ownerMemberId,
      PostcardSourceKind sourceKind,
      Long sourceId,
      String sourceContent,
      String sourceNote,
      String sourcePetSpecies,
      String sourcePetBreed,
      String sourcePhotoKey,
      String title,
      String text,
      PostcardStatus status,
      String errorMessage,
      String imageKey,
      LocalDate requestedOn,
      Instant createdAt,
      Instant processingStartedAt,
      Instant completedAt) {
    if (id == null) throw new InvalidPostcardException("엽서 값이 비어 있어요.");
    return new Postcard(
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

  public boolean belongsTo(Long petId) {
    return this.petId.equals(petId);
  }

  // "<이름>와 함께한 작은 순간". 이름 끝에 받침이 있으면 "과"를 붙인다.
  static String title(String petName) {
    String name = petName == null ? "" : petName.strip();
    if (name.isEmpty()) return "함께한 작은 순간";
    char last = name.charAt(name.length() - 1);
    boolean batchim = last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
    return name + (batchim ? "과" : "와") + " 함께한 작은 순간";
  }

  private static String head(String content) {
    int end = content.offsetByCodePoints(0, Math.min(TEXT_LENGTH, content.codePointCount(0, content.length())));
    return content.substring(0, end);
  }
}
