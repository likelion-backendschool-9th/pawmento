package com.pawmento.community.application.port.in;

import java.util.List;

// nextCursor는 더 불러올 글이 없으면 null이다
public record PostPageResult(List<PostSummaryResult> posts, Long nextCursor) {
}
