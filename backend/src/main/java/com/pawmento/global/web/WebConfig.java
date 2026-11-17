package com.pawmento.global.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

	// 우리 패키지의 @RestController 경로 앞에 /api/v1을 붙인다. 컨트롤러에는 /members처럼 나머지 경로만 쓴다.
	// 라이브러리 컨트롤러(Swagger 등)와 /error에는 붙지 않는다.
	// Spring Security는 MVC보다 먼저 실제 경로를 보므로 접두어가 붙지 않는다. Security 경로는 ApiPaths.V1로 전체 경로를 쓴다
	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(ApiPaths.V1, HandlerTypePredicate.forBasePackage("com.pawmento")
			.and(HandlerTypePredicate.forAnnotation(RestController.class)));
	}

}
