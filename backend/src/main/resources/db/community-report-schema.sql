-- 하나의 DO 문을 트랜잭션으로 실행한다. 동시 초기화도 테이블 잠금으로 직렬화한다.
DO $$
BEGIN
  LOCK TABLE post_reports IN SHARE ROW EXCLUSIVE MODE;
  -- 글은 soft delete라 이 FK가 글 삭제를 막지 않는다
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
      WHERE conname = 'fk_post_reports_post' AND conrelid = 'post_reports'::regclass) THEN
    ALTER TABLE post_reports ADD CONSTRAINT fk_post_reports_post
      FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE RESTRICT NOT DEFERRABLE;
  END IF;
  -- 같은 회원의 같은 글 처리 대기 신고는 하나다. 이름은 PostReportJpaEntity.PENDING_UNIQUE_INDEX와 같다
  CREATE UNIQUE INDEX IF NOT EXISTS ux_post_reports_pending
    ON post_reports(post_id, reporter_id) WHERE status = 'PENDING';
  -- 운영 목록: 상태별 id 내림차순 커서
  CREATE INDEX IF NOT EXISTS ix_post_reports_status_id ON post_reports(status, id DESC);
  -- 재신고 판정: 같은 회원의 같은 글 신고 중 가장 최근 것
  CREATE INDEX IF NOT EXISTS ix_post_reports_post_reporter ON post_reports(post_id, reporter_id, id DESC);
END $$;
