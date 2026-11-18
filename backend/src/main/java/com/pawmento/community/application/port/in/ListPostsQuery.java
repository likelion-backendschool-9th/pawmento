package com.pawmento.community.application.port.in;

// size의 기본값과 범위, cursor의 범위는 웹 요청(ListPostsRequest)이 검사한다. cursor는 첫 페이지면 null이다
public record ListPostsQuery(String category, int size, Long cursor) {
}
