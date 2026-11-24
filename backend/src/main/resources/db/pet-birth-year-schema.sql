-- Hibernate가 nullable birth_year를 추가한 뒤 실행한다. 기존 행도 검사한다.
DO $$
BEGIN
  LOCK TABLE pets IN SHARE ROW EXCLUSIVE MODE;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_pets_birth_information' AND conrelid = 'pets'::regclass) THEN
    ALTER TABLE pets ADD CONSTRAINT ck_pets_birth_information
      CHECK (birthday IS NULL OR birth_year IS NULL);
  END IF;
END $$;
