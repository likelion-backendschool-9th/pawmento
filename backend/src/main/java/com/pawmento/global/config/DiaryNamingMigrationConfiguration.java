package com.pawmento.global.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
class DiaryNamingMigrationConfiguration {
 @Bean
 static BeanFactoryPostProcessor migrateDiaryNamesBeforeHibernate() {
  return factory->{
   if(factory.containsBeanDefinition("entityManagerFactory")) {
    var entityManager=factory.getBeanDefinition("entityManagerFactory");
    var dependencies=new ArrayList<String>();
    if(entityManager.getDependsOn()!=null) dependencies.addAll(Arrays.asList(entityManager.getDependsOn()));
    dependencies.add("diaryNamingMigration");
    entityManager.setDependsOn(dependencies.toArray(String[]::new));
   }
  };
 }
 @Bean
 InitializingBean diaryNamingMigration(DataSource dataSource) {
  return ()->new JdbcTemplate(dataSource).execute(new ClassPathResource("db/diary-naming-migration.sql").getContentAsString(StandardCharsets.UTF_8));
 }
}
