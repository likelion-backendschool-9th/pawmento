-- Hibernate가 새 이름으로 빈 테이블을 만들기 전에 기존 행·FK·ID를 보존해 이름만 바꾼다.
DO $$
BEGIN
  PERFORM pg_advisory_xact_lock(hashtextextended('pawmento-diary-naming-v2',0));
  IF to_regclass('record_location') IS NOT NULL THEN
    IF to_regclass('diary_location') IS NOT NULL THEN
      RAISE EXCEPTION 'Both record_location and diary_location exist; resolve the naming migration before startup';
    END IF;
    ALTER TABLE record_location RENAME TO diary_location;
  END IF;
  IF to_regclass('diary_location') IS NOT NULL THEN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='diary_location' AND column_name='record_id') THEN
      IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='diary_location' AND column_name='diary_id') THEN
        RAISE EXCEPTION 'Both record_id and diary_id exist on diary_location';
      END IF;
      ALTER TABLE diary_location RENAME COLUMN record_id TO diary_id;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='diary_location'::regclass AND conname='uk_record_location_record') THEN
      ALTER TABLE diary_location RENAME CONSTRAINT uk_record_location_record TO uk_diary_location_diary;
    END IF;
  END IF;
  IF to_regclass('idx_record_location_pin_lookup') IS NOT NULL AND to_regclass('idx_diary_location_pin_lookup') IS NULL THEN
    ALTER INDEX idx_record_location_pin_lookup RENAME TO idx_diary_location_pin_lookup;
  END IF;
  IF to_regclass('record_location_id_seq') IS NOT NULL AND to_regclass('diary_location_id_seq') IS NULL THEN
    ALTER SEQUENCE record_location_id_seq RENAME TO diary_location_id_seq;
  END IF;
  IF to_regclass('diary_attachments') IS NOT NULL AND EXISTS (
      SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='diary_attachments' AND column_name='record_id') THEN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='diary_attachments' AND column_name='diary_id') THEN
      RAISE EXCEPTION 'Both record_id and diary_id exist on diary_attachments';
    END IF;
    ALTER TABLE diary_attachments RENAME COLUMN record_id TO diary_id;
  END IF;
END $$;
