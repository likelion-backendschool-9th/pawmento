-- 하나의 DO 문을 트랜잭션으로 실행한다. 동시 초기화도 테이블 잠금으로 직렬화한다.
DO $$
BEGIN
  LOCK TABLE diary_entries IN SHARE ROW EXCLUSIVE MODE;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
      WHERE conname = 'fk_diary_entries_pet' AND conrelid = 'diary_entries'::regclass) THEN
    ALTER TABLE diary_entries ADD CONSTRAINT fk_diary_entries_pet
      FOREIGN KEY (pet_id) REFERENCES pets(id) ON DELETE RESTRICT NOT DEFERRABLE;
  END IF;
  -- 기존 행은 미이관(NULL)으로 남겨 NONE으로 기존 지도 값을 덮어쓰지 않는다.
  ALTER TABLE diary_entries ADD COLUMN IF NOT EXISTS location_mode VARCHAR(16);
  ALTER TABLE diary_entries ADD COLUMN IF NOT EXISTS location_latitude DOUBLE PRECISION;
  ALTER TABLE diary_entries ADD COLUMN IF NOT EXISTS location_longitude DOUBLE PRECISION;
  ALTER TABLE diary_entries ADD COLUMN IF NOT EXISTS location_place_name VARCHAR(255);
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
      WHERE conname='ck_diary_entries_location_intent' AND conrelid='diary_entries'::regclass) THEN
    ALTER TABLE diary_entries ADD CONSTRAINT ck_diary_entries_location_intent CHECK (
      (location_mode IS NULL AND location_latitude IS NULL AND location_longitude IS NULL AND location_place_name IS NULL)
      OR (location_mode IS NOT NULL AND location_mode='NONE' AND location_latitude IS NULL AND location_longitude IS NULL AND location_place_name IS NULL)
      OR (location_mode IS NOT NULL AND location_mode='MANUAL' AND location_latitude IS NOT NULL AND location_longitude IS NOT NULL
          AND location_latitude BETWEEN -90 AND 90 AND location_longitude BETWEEN -180 AND 180)
    );
  END IF;
  CREATE INDEX IF NOT EXISTS ix_diary_entries_pet_date_id
    ON diary_entries(pet_id, recorded_date DESC, id DESC);
  -- 복합 인덱스의 첫 컬럼으로 FK 검사와 petId 존재 조회도 처리한다.
  DROP INDEX IF EXISTS ix_diary_entries_pet_id;
  CREATE TABLE IF NOT EXISTS diary_attachments (
    diary_id BIGINT NOT NULL REFERENCES diary_entries(id) ON DELETE CASCADE,
    asset_id BIGINT NOT NULL REFERENCES media_assets(id) ON DELETE RESTRICT,
    sort_order INTEGER NOT NULL CHECK(sort_order>=0),
    PRIMARY KEY(diary_id,asset_id),
    UNIQUE(diary_id,sort_order)
  );
  -- JPA also maps this table; CREATE TABLE IF NOT EXISTS alone does not add constraints after Hibernate creates it.
  -- Renaming record_id does not rename its constraints. Match the definition, not its name.
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid='diary_attachments'::regclass AND contype='f'
      AND confrelid='diary_entries'::regclass AND confdeltype='c'
      AND conkey=ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid='diary_attachments'::regclass AND attname='diary_id')]::smallint[]
      AND confkey=ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid='diary_entries'::regclass AND attname='id')]::smallint[]
  ) THEN
    ALTER TABLE diary_attachments ADD CONSTRAINT diary_attachments_diary_id_fkey FOREIGN KEY (diary_id) REFERENCES diary_entries(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='diary_attachments'::regclass AND conname='diary_attachments_asset_id_fkey') THEN
    ALTER TABLE diary_attachments ADD CONSTRAINT diary_attachments_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES media_assets(id) ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid='diary_attachments'::regclass AND contype='u' AND cardinality(conkey)=2
      AND conkey @> ARRAY[
        (SELECT attnum FROM pg_attribute WHERE attrelid='diary_attachments'::regclass AND attname='diary_id'),
        (SELECT attnum FROM pg_attribute WHERE attrelid='diary_attachments'::regclass AND attname='sort_order')
      ]::smallint[]
  ) THEN
    ALTER TABLE diary_attachments ADD CONSTRAINT diary_attachments_diary_id_sort_order_key UNIQUE (diary_id,sort_order);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='diary_attachments'::regclass AND conname='diary_attachments_sort_order_check') THEN
    ALTER TABLE diary_attachments ADD CONSTRAINT diary_attachments_sort_order_check CHECK (sort_order>=0);
  END IF;
  CREATE INDEX IF NOT EXISTS ix_diary_attachments_asset ON diary_attachments(asset_id);
END $$;
