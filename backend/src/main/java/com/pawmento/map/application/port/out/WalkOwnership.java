package com.pawmento.map.application.port.out;

/**
 * 산책이 어느 반려동물의 것이고 누가 시작했는지만 담은 값. 산책을 잠그기 전에 소유자를 확인하고 잠글 반려동물을 알아내는 용도다.
 * 산책의 상태(끝난 시각, 마지막 활동 시각)를 담지 않아서 잠그기 전에 읽은 값으로 상태를 판단하는 일이 생기지 않는다.
 */
public record WalkOwnership(Long petId, Long createdBy) {
}
