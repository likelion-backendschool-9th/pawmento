package com.pawmento.question.adapter.out.persistence;

import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class QuestionProgressSchemaConfiguration {
  @Bean
  InitializingBean questionProgressSchema(
      EntityManagerFactory entityManagerFactory,
      DataSource source,
      PlatformTransactionManager manager) {
    return () -> {
      var sql =
          new ClassPathResource("db/question-progress-schema.sql")
              .getContentAsString(StandardCharsets.UTF_8);
      new TransactionTemplate(manager)
          .executeWithoutResult(status -> new JdbcTemplate(source).execute(sql));
    };
  }
}
