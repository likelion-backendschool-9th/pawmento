export const MAX_EXTRA_ATTACHMENTS = 5;
export const MAX_PRIMARY_PHOTO_BYTES = 1024 * 1024;
export const MAX_ATTACHMENT_BYTES = 2 * 1024 * 1024;

type SelectedFile = { type: string; size: number };

/**
 * Checks a photo selection as one unit and decides which file becomes the primary photo.
 * The first file fills an empty primary slot only on forms that have one.
 */
export function planPhotoSelection(
  files: readonly SelectedFile[],
  { attachmentCount, hasPrimarySlot, hasPrimaryPhoto }: {
    attachmentCount: number;
    hasPrimarySlot: boolean;
    hasPrimaryPhoto: boolean;
  },
): { primaryIndex: number | null } {
  const primaryIndex = hasPrimarySlot && !hasPrimaryPhoto && files.length ? 0 : null;
  const extra = files.length - (primaryIndex === null ? 0 : 1);
  if (attachmentCount + extra > MAX_EXTRA_ATTACHMENTS)
    throw Error(`기록 하나에 추가 첨부는 ${MAX_EXTRA_ATTACHMENTS}개까지 가능해요.`);
  files.forEach((file, index) => {
    if (!/^image\/(jpeg|png|webp)$/.test(file.type))
      throw Error("JPG, PNG, WebP 사진을 선택해 주세요.");
    if (index === primaryIndex && file.size > MAX_PRIMARY_PHOTO_BYTES)
      throw Error("첫 사진은 1MB 이하로 선택해 주세요.");
    if (file.size > MAX_ATTACHMENT_BYTES)
      throw Error("브라우저 데모에서는 첨부 파일 한 개가 2MB 이하여야 해요.");
  });
  return { primaryIndex };
}

/** Checks a voice file chosen where the browser cannot record. */
export function checkVoiceFile(file: SelectedFile, attachmentCount: number) {
  if (attachmentCount >= MAX_EXTRA_ATTACHMENTS)
    throw Error(`추가 첨부는 ${MAX_EXTRA_ATTACHMENTS}개까지 가능해요.`);
  if (!file.type.startsWith("audio/")) throw Error("음성 파일을 선택해 주세요.");
  if (file.size > MAX_ATTACHMENT_BYTES)
    throw Error("브라우저 데모에서는 첨부 파일 한 개가 2MB 이하여야 해요.");
}

/** Keeps the visible slide inside the list after photos are removed. */
export function clampSlide(position: number, count: number) {
  return Math.max(0, Math.min(position, count - 1));
}
