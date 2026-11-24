package com.pawmento.pet.adapter.out.persistence;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class PetBirthYearSchemaConfiguration {
	@Bean
	InitializingBean petBirthYearSchema(EntityManagerFactory entityManagerFactory, DataSource dataSource,
			PlatformTransactionManager transactionManager) {
		return () -> {
			var sql = new ClassPathResource("db/pet-birth-year-schema.sql").getContentAsString(StandardCharsets.UTF_8);
			new TransactionTemplate(transactionManager)
					.executeWithoutResult(status -> new JdbcTemplate(dataSource).execute(sql));
		};
	}
}
