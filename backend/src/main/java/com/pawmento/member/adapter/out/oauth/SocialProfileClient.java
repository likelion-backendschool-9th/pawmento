package com.pawmento.member.adapter.out.oauth;

import com.pawmento.member.application.port.out.SocialProfile;
import com.pawmento.member.domain.AuthProvider;

public interface SocialProfileClient {
    
    AuthProvider provider();

    SocialProfile load(String code, String state, String redirectUri);
}
