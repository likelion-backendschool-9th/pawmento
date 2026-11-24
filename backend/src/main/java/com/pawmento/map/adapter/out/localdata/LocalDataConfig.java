package com.pawmento.map.adapter.out.localdata;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LocalDataProperties.class)
class LocalDataConfig {

}
