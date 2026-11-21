-- 하나의 DO 문을 트랜잭션으로 실행한다. 동시 초기화도 테이블 잠금으로 직렬화한다.
DO $$
BEGIN
  LOCK TABLE postcards IN SHARE ROW EXCLUSIVE MODE;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
      WHERE conname = 'fk_postcard_pet' AND conrelid = 'postcards'::regclass) THEN
    ALTER TABLE postcards ADD CONSTRAINT fk_postcard_pet
      FOREIGN KEY (pet_id) REFERENCES pets(id) ON DELETE RESTRICT NOT DEFERRABLE;
  END IF;
  CREATE INDEX IF NOT EXISTS ix_postcards_pet_id ON postcards(pet_id, id DESC);
  CREATE INDEX IF NOT EXISTS ix_postcards_processing
    ON postcards(id) WHERE status = 'PROCESSING';
END $$;

-- 하루 횟수. 엽서 행과 따로 세어 엽서를 지워도 줄지 않는다.
CREATE TABLE IF NOT EXISTS postcard_daily_total (
  usage_date DATE PRIMARY KEY,
  count INTEGER NOT NULL CHECK (count >= 0)
);
CREATE TABLE IF NOT EXISTS postcard_daily_usage (
  usage_date DATE NOT NULL,
  member_id BIGINT NOT NULL,
  count INTEGER NOT NULL CHECK (count >= 0),
  PRIMARY KEY (usage_date, member_id)
);
