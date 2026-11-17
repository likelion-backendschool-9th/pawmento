package com.pawmento.memory.domain;

public class Memory {
    private Long id;
    private Long petId;
    private String content;

    // ① private 생성자: 클래스 밖에서는 new Memory(...) 불가
    private Memory(Long petId, String content) {
        this.petId = petId;
        this.content = content;
        // id는 안 넣음. DB에 저장해야 생기니까 지금은 null
    }

    // ② 유일한 생성 통로: 규칙 검사 → 통과하면 객체 생성
    public static Memory create(Long petId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("내용이 비었습니다.");
        }
        return new Memory(petId, content);
    }

    // ③ getter: 어댑터가 MemoryJpaEntity로 옮겨 담을 때 사용
    public Long getId() {
        return id;
    }

    public Long getPetId() {
        return petId;
    }

    public String getContent() {
        return content;
    }
}