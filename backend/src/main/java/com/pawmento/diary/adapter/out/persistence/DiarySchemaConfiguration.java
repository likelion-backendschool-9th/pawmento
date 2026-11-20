package com.pawmento.diary.adapter.out.persistence;

import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

// ddl-auto update는 Long 식별자만 있는 도메인 간 FK를 만들지 않는다.
// EntityManagerFactory 초기화(테이블 생성) 이후 명시적인 PostgreSQL 제약을 적용한다.
@Configuration(proxyBeanMethods = false)
class DiarySchemaConfiguration {
  @Bean
  InitializingBean diarySchema(
      EntityManagerFactory entityManagerFactory,
      DataSource dataSource,
      org.springframework.transaction.PlatformTransactionManager transactionManager) {
    return () -> {
      var jdbc = new JdbcTemplate(dataSource);
      var sql =
          new ClassPathResource("db/diary-schema.sql").getContentAsString(StandardCharsets.UTF_8);
      new org.springframework.transaction.support.TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                jdbc.execute(sql);
                DiaryLocationBackfill.run(jdbc);
              });
    };
  }
}
