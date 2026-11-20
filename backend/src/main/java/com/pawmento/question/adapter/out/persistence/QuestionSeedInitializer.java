package com.pawmento.question.adapter.out.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 시작 시에만 사용하는 영속화 경계. 버전 SQL이 잠금과 seed_key 검사를 수행한다. */
final class QuestionSeedInitializer {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final Resource seed;

  QuestionSeedInitializer(JdbcTemplate jdbc, PlatformTransactionManager manager, Resource seed) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(manager);
    this.seed = seed;
  }

  void initialize() throws IOException {
    String sql = seed.getContentAsString(StandardCharsets.UTF_8);
    transaction.executeWithoutResult(status -> jdbc.execute(sql));
  }
}
