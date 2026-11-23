package com.pawmento.postcard.application.port.in;

public record GetPostcardImageResult(String filename, String contentType, byte[] bytes) {}
