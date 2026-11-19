package com.pawmento.diary.application.port.in;

public interface GetDiaryFileUseCase {
  GetDiaryFileResult get(GetDiaryFileQuery query);
}
