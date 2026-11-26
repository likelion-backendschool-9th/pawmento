package com.pawmento.community.adapter.out.persistence.report;

import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManagerFactory;

// ddl-auto update는 FK와 부분 고유 인덱스를 만들지 않는다. 테이블 생성 뒤 적용한다.
// EntityManagerFactory를 받아 ddl-auto가 테이블을 만든 다음에 실행되게 한다
@Configuration(proxyBeanMethods = false)
class ReportSchemaConfiguration {

	@Bean
	InitializingBean communityReportSchema(EntityManagerFactory entityManagerFactory, DataSource dataSource,
			PlatformTransactionManager transactionManager) {
		return () -> {
			String sql = new ClassPathResource("db/community-report-schema.sql")
				.getContentAsString(StandardCharsets.UTF_8);
			new TransactionTemplate(transactionManager)
				.executeWithoutResult(status -> new JdbcTemplate(dataSource).execute(sql));
		};
	}

}
