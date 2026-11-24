package com.pawmento.pet.application.port.in;

public interface ChangePetOwnerUseCase {

	/** 호출자의 트랜잭션에 참여한다. 가족 멤버십 검사와 변경은 호출자가 맡는다. */
	void changeOwner(ChangePetOwnerCommand command);

}
