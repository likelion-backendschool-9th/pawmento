package com.pawmento.family.application.port.in;

import java.util.List;

public interface ListMyFamilyPetsUseCase {

    List<MyFamilyPetResult> list(ListMyFamilyPetsQuery query);

}
