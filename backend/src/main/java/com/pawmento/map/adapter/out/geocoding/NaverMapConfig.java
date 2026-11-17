package com.pawmento.map.adapter.out.geocoding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NaverMapProperties.class)
class NaverMapConfig {

}
