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
