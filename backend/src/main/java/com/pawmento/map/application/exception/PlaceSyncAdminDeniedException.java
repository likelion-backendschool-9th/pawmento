package com.pawmento.map.application.exception;

import com.pawmento.global.exception.PermissionDeniedException;

// 관리자가 아니어서 장소를 가져올 수 없다(#172-S22)
public final class PlaceSyncAdminDeniedException extends PermissionDeniedException {

	public PlaceSyncAdminDeniedException() {
		super("관리자만 장소를 가져올 수 있습니다.");
	}

}
