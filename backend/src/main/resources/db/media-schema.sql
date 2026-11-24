DO $$
BEGIN
  LOCK TABLE media_assets IN SHARE ROW EXCLUSIVE MODE;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_media_assets_purpose' AND conrelid = 'media_assets'::regclass) THEN
    ALTER TABLE media_assets DROP CONSTRAINT IF EXISTS media_assets_purpose_check;
    ALTER TABLE media_assets ADD CONSTRAINT ck_media_assets_purpose
      CHECK (purpose IN ('DIARY_ATTACHMENT', 'QUESTION_ANSWER_ATTACHMENT', 'PET_PROFILE'));
  END IF;
END $$;
