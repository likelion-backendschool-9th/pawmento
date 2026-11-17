package com.pawmento.pet.application.service;

import java.security.SecureRandom;

import com.pawmento.pet.application.port.out.LoadPetPort;

// 소유자 범위에서 짧은 태그를 발급한다. (owner_member_id, tag) 유니크 가정
final class PetTagGenerator {

	private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
	private static final int TAG_LENGTH = 4;
	private static final int MAX_ATTEMPTS = 32;

	private final LoadPetPort loadPetPort;
	private final SecureRandom random = new SecureRandom();

	PetTagGenerator(LoadPetPort loadPetPort) {
		this.loadPetPort = loadPetPort;
	}

	String nextTag(Long ownerMemberId) {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String tag = randomTag();
			if (!loadPetPort.existsByOwnerMemberIdAndTag(ownerMemberId, tag)) {
				return tag;
			}
		}
		throw new IllegalStateException("반려동물 태그를 발급하지 못했습니다.");
	}

	private String randomTag() {
		char[] chars = new char[TAG_LENGTH];
		for (int i = 0; i < TAG_LENGTH; i++) {
			chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
		}
		return new String(chars);
	}

}
