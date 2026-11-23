package com.pawmento.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;
import com.pawmento.global.web.ApiPaths;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorResponder errorResponder) {
		return http
			// 토큰 쿠키는 SameSite=Lax라 다른 사이트의 POST·PUT·DELETE 요청에 붙지 않는다. CSRF 토큰을 따로 쓰지 않는다.
			// 전제: GET 요청은 데이터를 바꾸지 않는다
			.csrf(AbstractHttpConfigurer::disable)
			// 화면은 프론트엔드가 맡는다. 기본 로그인 페이지로 넘기거나 브라우저 인증 창을 띄우지 않는다
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			// 로그인 상태는 토큰에만 있다. 서버 세션을 만들지 않는다
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				// 처리 중 오류로 넘어가는 내부 요청(/error)이 401로 가려지지 않게 한다
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(PublicEndpoints.AUTH, PublicEndpoints.PUBLIC_READ, PublicEndpoints.API_DOCS).permitAll()
				.requestMatchers(ApiPaths.V1 + "/admin", ApiPaths.V1 + "/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.oauth2ResourceServer(resourceServer -> resourceServer
				.bearerTokenResolver(new CookieBearerTokenResolver())
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
				.authenticationEntryPoint(errorResponder)
				.accessDeniedHandler(errorResponder))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(errorResponder)
				.accessDeniedHandler(errorResponder))
			.build();
	}

	// 토큰의 역할(USER, ADMIN)을 ROLE_USER, ROLE_ADMIN 권한으로 바꾼다. 관리자 전용 경로를 hasRole("ADMIN")로 막을 수 있다
	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(JwtClaims.ROLE);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

}
