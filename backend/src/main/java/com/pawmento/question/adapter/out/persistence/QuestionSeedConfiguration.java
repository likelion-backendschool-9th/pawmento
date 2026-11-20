package com.pawmento.question.adapter.out.persistence;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
class QuestionSeedConfiguration {
  @Bean
  InitializingBean questionSeed(
      EntityManagerFactory entityManagerFactory,
      DataSource dataSource,
      PlatformTransactionManager transactionManager) {
    return () ->
        new QuestionSeedInitializer(
                new JdbcTemplate(dataSource),
                transactionManager,
                new ClassPathResource("db/question-seed-v1.sql"))
            .initialize();
  }
}
