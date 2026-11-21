package com.pawmento.postcard.adapter.out.persistence;

import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// ddl-auto update는 도메인 간 FK와 CHECK를 만들지 않는다. 테이블 생성 뒤 제약을 적용한다.
@Configuration(proxyBeanMethods = false)
class PostcardSchemaConfiguration {
  @Bean
  InitializingBean postcardSchema(
      EntityManagerFactory entityManagerFactory,
      DataSource dataSource,
      PlatformTransactionManager transactionManager) {
    return () -> {
      var sql =
          new ClassPathResource("db/postcard-schema.sql").getContentAsString(StandardCharsets.UTF_8);
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(status -> new JdbcTemplate(dataSource).execute(sql));
    };
  }
}
