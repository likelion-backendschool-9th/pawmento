package com.pawmento.media.adapter.out.persistence;

import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// ddl-auto update는 기존 enum CHECK에 새 용도를 추가하지 않는다.
@Configuration(proxyBeanMethods = false)
class MediaSchemaConfiguration {
  @Bean
  InitializingBean mediaSchema(
      EntityManagerFactory entityManagerFactory,
      DataSource dataSource,
      PlatformTransactionManager transactionManager) {
    return () -> {
      var sql =
          new ClassPathResource("db/media-schema.sql").getContentAsString(StandardCharsets.UTF_8);
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(status -> new JdbcTemplate(dataSource).execute(sql));
    };
  }
}
