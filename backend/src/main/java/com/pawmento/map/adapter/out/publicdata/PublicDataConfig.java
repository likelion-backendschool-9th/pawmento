package com.pawmento.map.adapter.out.publicdata;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PublicDataProperties.class)
class PublicDataConfig {

}
