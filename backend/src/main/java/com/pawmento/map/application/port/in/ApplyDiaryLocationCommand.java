package com.pawmento.map.application.port.in;

public record ApplyDiaryLocationCommand(
    Long diaryId, Long petId, Long createdBy, long revision, DiaryLocationValue location) {
  public ApplyDiaryLocationCommand {
    validate(diaryId, petId, createdBy, revision);
  }

  static void validate(Long diary, Long pet, Long author, long revision) {
    if (diary == null
        || diary <= 0
        || pet == null
        || pet <= 0
        || author == null
        || author <= 0
        || revision < 0) throw new IllegalArgumentException("내부 위치 명령의 식별자/revision이 올바르지 않습니다.");
  }
}
