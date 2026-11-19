package com.pawmento.member.adapter.out.oauth;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.pawmento.member.application.exception.UnsupportedSocialProviderException;
import com.pawmento.member.application.port.out.LoadSocialProfilePort;
import com.pawmento.member.application.port.out.SocialProfile;
import com.pawmento.member.domain.AuthProvider;

@Component 
public class SocialProfileAdapter implements LoadSocialProfilePort {

    private final Map<AuthProvider, SocialProfileClient> clients = new EnumMap<>(AuthProvider.class);

    // Spring이 SocialProfileClient 구현 빈을 모두 찾아 리스트로 넣어 준다
    SocialProfileAdapter(List<SocialProfileClient> clientList) {
        for (SocialProfileClient client : clientList) {
            clients.put(client.provider(), client);
        }
    }

    @Override
    public SocialProfile load(AuthProvider provider, String code, String state, String redirectUri) {
        SocialProfileClient client = clients.get(provider);
        if (client == null) {
            throw new UnsupportedSocialProviderException();
        }
        return client.load(code, state, redirectUri);
    }
    
    
}
