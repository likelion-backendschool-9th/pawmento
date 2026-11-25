package com.pawmento.member.application.port.in;

import java.util.List;

public record ListMemberProfilesQuery(List<Long> memberIds) {

}
