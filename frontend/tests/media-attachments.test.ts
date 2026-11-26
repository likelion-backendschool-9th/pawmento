import assert from "node:assert/strict";
import test from "node:test";
import { checkVoiceFile, clampSlide, planPhotoSelection } from "../lib/mediaAttachments.ts";

const MB = 1024 * 1024;
const jpg = (size = MB / 2) => ({ type: "image/jpeg", size });
const form = { attachmentCount: 0, hasPrimarySlot: true, hasPrimaryPhoto: false };

test("#233-S4 대표 사진 자리가 비어 있으면 첫 사진만 대표 사진이 되고 나머지는 추가 첨부가 된다", () => {
  assert.deepEqual(planPhotoSelection([jpg(), jpg()], form), { primaryIndex: 0 });
  assert.deepEqual(planPhotoSelection([jpg()], { ...form, hasPrimaryPhoto: true }), { primaryIndex: null });
  assert.deepEqual(planPhotoSelection([jpg()], { ...form, hasPrimarySlot: false }), { primaryIndex: null });
});

test("#233-S4 추가 첨부 5개 한도는 대표 사진이 될 한 장을 빼고 센다", () => {
  assert.doesNotThrow(() => planPhotoSelection(Array.from({ length: 6 }, () => jpg()), form));
  assert.throws(() => planPhotoSelection(Array.from({ length: 7 }, () => jpg()), form), /5개까지/);
  assert.doesNotThrow(() => planPhotoSelection([jpg()], { ...form, attachmentCount: 4, hasPrimaryPhoto: true }));
  assert.throws(() => planPhotoSelection([jpg(), jpg()], { ...form, attachmentCount: 4, hasPrimaryPhoto: true }), /5개까지/);
  assert.throws(() => planPhotoSelection([jpg()], { ...form, attachmentCount: 5, hasPrimarySlot: false }), /5개까지/);
});

test("#233-S4 첫 사진은 1MB, 추가 사진은 2MB까지이고 하나라도 틀리면 전체를 거절한다", () => {
  assert.throws(() => planPhotoSelection([jpg(MB + 1)], form), /첫 사진은 1MB/);
  assert.doesNotThrow(() => planPhotoSelection([jpg(MB), jpg(2 * MB)], form));
  assert.throws(() => planPhotoSelection([jpg(), jpg(2 * MB + 1)], form), /2MB/);
  assert.doesNotThrow(() => planPhotoSelection([jpg(2 * MB)], { ...form, hasPrimaryPhoto: true }));
  assert.throws(() => planPhotoSelection([jpg(), { type: "image/gif", size: 10 }], form), /JPG, PNG, WebP/);
});

test("#233-S4 녹음을 못 하는 환경의 목소리 파일도 형식·크기·개수를 확인한다", () => {
  assert.doesNotThrow(() => checkVoiceFile({ type: "audio/mpeg", size: MB }, 4));
  assert.throws(() => checkVoiceFile({ type: "audio/mpeg", size: MB }, 5), /5개까지/);
  assert.throws(() => checkVoiceFile({ type: "image/png", size: MB }, 0), /음성 파일/);
  assert.throws(() => checkVoiceFile({ type: "audio/webm", size: 2 * MB + 1 }, 0), /2MB/);
});

test("#233-S4 사진을 지워도 슬라이드 위치는 남은 사진 범위 안에 머문다", () => {
  assert.equal(clampSlide(2, 2), 1);
  assert.equal(clampSlide(2, 1), 0);
  assert.equal(clampSlide(-1, 3), 0);
  assert.equal(clampSlide(1, 3), 1);
});
