package com.pawmento.community.application.port.in;

public record EditPostCommand(Long memberId, Long postId, String category, String title, String body) {
}
