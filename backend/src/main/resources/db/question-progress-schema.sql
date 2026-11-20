SELECT pg_advisory_xact_lock(hashtextextended('pawmento-question-progress-schema',0));
DO $schema$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='question_prompt'::regclass AND conname='fk_question_prompt_question') THEN
    ALTER TABLE question_prompt ADD CONSTRAINT fk_question_prompt_question FOREIGN KEY(question_id) REFERENCES question(id) ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='question_progress'::regclass AND conname='fk_question_progress_current') THEN
    ALTER TABLE question_progress ADD CONSTRAINT fk_question_progress_current FOREIGN KEY(current_prompt_id,pet_id,mode) REFERENCES question_prompt(id,pet_id,mode) ON DELETE RESTRICT;
  END IF;
END;
$schema$;
