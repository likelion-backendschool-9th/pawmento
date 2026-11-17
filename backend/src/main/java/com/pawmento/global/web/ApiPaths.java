package com.pawmento.global.web;

// API 경로의 공통 접두어. WebConfig가 컨트롤러에 붙이고, Security 경로(PublicEndpoints)도 이 값으로 전체 경로를 만든다
public final class ApiPaths {

	public static final String V1 = "/api/v1";

	private ApiPaths() {
	}

}
