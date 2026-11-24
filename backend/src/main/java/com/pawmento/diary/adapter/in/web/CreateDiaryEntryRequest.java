package com.pawmento.diary.adapter.in.web;

import tools.jackson.databind.JsonNode;

public record CreateDiaryEntryRequest(
    JsonNode content,
    JsonNode recordedDate,
    JsonNode attachmentIds,
    JsonNode location,
    // 아래 세 필드는 받는 값이 아니다. 작성자 위조를 막으려고 두었고, 값이 오면 명시적 null이어도 400이다.
    // 작성자는 로그인 회원으로 정하고 이름은 서버가 회원 정보에서 저장한다(DiaryRequestValues.rejectAuthor).
    // createdBy는 이 PR 전부터 body로 와도 무시하는 계약(#32-S2)이라 거절 대상에 넣지 않았다.
    JsonNode author,
    JsonNode authorNameSnapshot,
    JsonNode displayName) {}
