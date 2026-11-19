package com.pawmento.diary.application.service;

import com.pawmento.diary.application.exception.*;
import com.pawmento.diary.application.port.out.LoadDiaryEntryPort;
import com.pawmento.diary.domain.DiaryEntry;
import com.pawmento.diary.domain.exception.InvalidDiaryEntryException;

final class DiaryEntryAccess {
  private DiaryEntryAccess() {}

  static DiaryEntry load(LoadDiaryEntryPort load, Long petId, Long id) {
    var entry = load.findById(id).orElseThrow(DiaryEntryNotFoundException::new);
    if (!entry.getPetId().equals(petId)) throw new DiaryEntryNotFoundException();
    return entry;
  }

  static DiaryEntry locked(LoadDiaryEntryPort load, Long petId, Long id) {
    var entry = load.findForUpdate(id).orElseThrow(DiaryVersionConflictException::new);
    if (!entry.getPetId().equals(petId)) throw new DiaryEntryNotFoundException();
    return entry;
  }

  static void version(DiaryEntry entry, Long version) {
    if (version == null || version < 0) throw new InvalidDiaryEntryException();
    if (!entry.getVersion().equals(version)) throw new DiaryVersionConflictException();
  }
}
