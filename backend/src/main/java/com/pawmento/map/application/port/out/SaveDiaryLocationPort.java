package com.pawmento.map.application.port.out;

import com.pawmento.map.domain.DiaryLocation;

public interface SaveDiaryLocationPort {

	// id가 없으면 새로 저장하고, 있으면 그 위치를 바꾼다. 저장된 위치의 id를 돌려준다
	Long save(DiaryLocation location);

}
