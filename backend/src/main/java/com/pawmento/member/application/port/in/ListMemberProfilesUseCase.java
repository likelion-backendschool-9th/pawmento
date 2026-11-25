package com.pawmento.member.application.port.in;

import java.util.List;

public interface ListMemberProfilesUseCase {

	List<MemberProfileResult> list(ListMemberProfilesQuery query);

}
