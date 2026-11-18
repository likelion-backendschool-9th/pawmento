package com.pawmento.community.application.port.in;

public record CreatePostCommand(Long memberId, String category, String title, String body) {
}
