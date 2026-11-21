package com.pawmento.postcard.adapter.out.persistence;

import com.pawmento.postcard.application.port.out.PostcardUsage;
import com.pawmento.postcard.application.port.out.PostcardUsagePort;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// 하루 횟수 행은 엽서와 따로 저장·잠그므로 어댑터를 나눈다.
@Component
@RequiredArgsConstructor
public class PostcardUsagePersistenceAdapter implements PostcardUsagePort {
  private final JdbcTemplate jdbc;

  @Override
  public PostcardUsage lock(LocalDate date, Long memberId) {
    jdbc.update(
        "INSERT INTO postcard_daily_total(usage_date, count) VALUES (?, 0) ON CONFLICT DO NOTHING",
        date);
    long total =
        jdbc.queryForObject(
            "SELECT count FROM postcard_daily_total WHERE usage_date = ? FOR UPDATE",
            Long.class,
            date);
    jdbc.update(
        "INSERT INTO postcard_daily_usage(usage_date, member_id, count) VALUES (?, ?, 0)"
            + " ON CONFLICT DO NOTHING",
        date,
        memberId);
    long member =
        jdbc.queryForObject(
            "SELECT count FROM postcard_daily_usage WHERE usage_date = ? AND member_id = ? FOR UPDATE",
            Long.class,
            date,
            memberId);
    return new PostcardUsage(member, total);
  }

  @Override
  public void increment(LocalDate date, Long memberId) {
    jdbc.update("UPDATE postcard_daily_total SET count = count + 1 WHERE usage_date = ?", date);
    jdbc.update(
        "UPDATE postcard_daily_usage SET count = count + 1 WHERE usage_date = ? AND member_id = ?",
        date,
        memberId);
  }

  @Override
  public long memberCount(LocalDate date, Long memberId) {
    return jdbc
        .queryForList(
            "SELECT count FROM postcard_daily_usage WHERE usage_date = ? AND member_id = ?",
            Long.class,
            date,
            memberId)
        .stream()
        .findFirst()
        .orElse(0L);
  }
}
