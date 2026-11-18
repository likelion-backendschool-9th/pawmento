package com.pawmento.community.application.port.in;

// size와 cursor는 보내지 않으면 null이다. size의 기본값과 범위는 서비스가 정한다
public record ListPostsQuery(String category, Integer size, Long cursor) {
}
