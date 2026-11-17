package com.pawmento.pet.application.port.in;

import java.util.List;

public interface ListPetsUseCase {

	List<PetResult> list(ListPetsQuery query);

}
