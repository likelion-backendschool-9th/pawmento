package com.pawmento.postcard.application.port.out;

// 원본에서 그림에 쓸 글과 열 수 있는 첫 사진. 사진이 없으면 photoAssetId가 null이다.
public record PostcardSource(String content, Long photoAssetId) {}
