-- 배포 이후 이 파일은 수정하지 않는다. 다음 버전도 같은 트랜잭션 잠금 키를 사용한다.
SELECT pg_advisory_xact_lock(hashtextextended('pawmento-question-seed', 0));

DO $seed$
DECLARE
  seeds jsonb := '[
    {"seed_key":"active-play-001","mode":"ACTIVE","content":"요즘 우리 아이가 가장 좋아하는 놀이는 무엇인가요?"},
    {"seed_key":"active-happy-001","mode":"ACTIVE","content":"오늘 우리 아이가 가장 신났던 순간은 언제인가요?"},
    {"seed_key":"garden-first-001","mode":"GARDEN","content":"우리 아이와 처음 만났던 날은 어땠나요?"},
    {"seed_key":"garden-habit-001","mode":"GARDEN","content":"지금도 떠오르는 우리 아이만의 귀여운 습관은 무엇인가요?"}
  ]';
BEGIN
  IF EXISTS (
    SELECT 1 FROM jsonb_to_recordset(seeds) AS expected(seed_key text, mode text, content text)
    JOIN question stored ON stored.seed_key = expected.seed_key
    WHERE stored.mode IS DISTINCT FROM expected.mode
  ) THEN
    RAISE EXCEPTION 'Question seed mode conflicts with existing data';
  END IF;

  INSERT INTO question(seed_key, mode, content, enabled, created_at, updated_at, version)
  SELECT seed_key, mode, content, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
  FROM jsonb_to_recordset(seeds) AS expected(seed_key text, mode text, content text)
  ORDER BY seed_key
  ON CONFLICT (seed_key) DO NOTHING;
END;
$seed$;
