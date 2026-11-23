package com.pawmento.map.adapter.out.access;

import org.springframework.stereotype.Component;

import com.pawmento.map.application.exception.PlaceSyncAdminDeniedException;
import com.pawmento.map.application.port.out.CheckPlaceSyncAdminPort;
import com.pawmento.member.application.exception.MemberNotFoundException;
import com.pawmento.member.application.port.in.GetMemberQuery;
import com.pawmento.member.application.port.in.GetMemberUseCase;

import lombok.RequiredArgsConstructor;

// 회원 모듈에서 지금 역할을 다시 읽어 관리자인지 확인한다. 질문 모듈의 관리자 확인과 같은 방식이다
@Component
@RequiredArgsConstructor
class PlaceSyncAdminAdapter implements CheckPlaceSyncAdminPort {

	private final GetMemberUseCase members;

	@Override
	public void requireAdmin(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new PlaceSyncAdminDeniedException();
		}
		try {
			if (!"ADMIN".equals(members.get(new GetMemberQuery(memberId)).role())) {
				throw new PlaceSyncAdminDeniedException();
			}
		}
		catch (MemberNotFoundException missing) {
			throw new PlaceSyncAdminDeniedException();
		}
	}

}
