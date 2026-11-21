package com.pawmento.postcard.application.port.out;

// 생성기에는 재료만 넘긴다. 프롬프트는 모델마다 맞는 방식이 달라 각 어댑터가 만든다.
// photoJpeg가 있으면 아이 모습을 사진에 맞추고, 없으면 종·품종으로 맞춘다.
public record PostcardImageRequest(
    String content, String note, String species, String breed, byte[] photoJpeg) {}
