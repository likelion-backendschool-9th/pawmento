-- 하나의 DO 문을 트랜잭션으로 실행한다. 동시 초기화도 테이블 잠금으로 직렬화한다.
DO $$
BEGIN
  LOCK TABLE diary_entries IN SHARE ROW EXCLUSIVE MODE;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
      WHERE conname = 'fk_diary_entries_pet' AND conrelid = 'diary_entries'::regclass) THEN
    ALTER TABLE diary_entries ADD CONSTRAINT fk_diary_entries_pet
      FOREIGN KEY (pet_id) REFERENCES pets(id) ON DELETE RESTRICT NOT DEFERRABLE;
  END IF;
  CREATE INDEX IF NOT EXISTS ix_diary_entries_pet_date_id
    ON diary_entries(pet_id, recorded_date DESC, id DESC);
  -- 복합 인덱스의 첫 컬럼으로 FK 검사와 petId 존재 조회도 처리한다.
  DROP INDEX IF EXISTS ix_diary_entries_pet_id;
END $$;
