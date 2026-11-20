SELECT pg_advisory_xact_lock(hashtextextended('pawmento-question-progress-schema',0));
DO $schema$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='question_prompt'::regclass AND conname='fk_question_prompt_question') THEN
    ALTER TABLE question_prompt ADD CONSTRAINT fk_question_prompt_question FOREIGN KEY(question_id) REFERENCES question(id) ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='question_progress'::regclass AND conname='fk_question_progress_current') THEN
    ALTER TABLE question_progress ADD CONSTRAINT fk_question_progress_current FOREIGN KEY(current_prompt_id,pet_id,mode) REFERENCES question_prompt(id,pet_id,mode) ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='question_answer'::regclass AND conname='fk_question_answer_prompt') THEN
    ALTER TABLE question_answer ADD CONSTRAINT fk_question_answer_prompt FOREIGN KEY(prompt_id,pet_id,mode) REFERENCES question_prompt(id,pet_id,mode) ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='question_answer'::regclass AND conname='fk_question_answer_pet') THEN
    ALTER TABLE question_answer ADD CONSTRAINT fk_question_answer_pet FOREIGN KEY(pet_id) REFERENCES pets(id) ON DELETE RESTRICT;
  END IF;
  CREATE TABLE IF NOT EXISTS question_answer_attachment (
    answer_id BIGINT NOT NULL REFERENCES question_answer(id) ON DELETE CASCADE,
    asset_id BIGINT NOT NULL REFERENCES media_assets(id) ON DELETE RESTRICT,
    sort_order INTEGER NOT NULL CHECK(sort_order>=0),
    PRIMARY KEY(answer_id,asset_id), UNIQUE(answer_id,sort_order)
  );
  CREATE INDEX IF NOT EXISTS ix_question_answer_attachment_asset ON question_answer_attachment(asset_id);
END;
$schema$;
