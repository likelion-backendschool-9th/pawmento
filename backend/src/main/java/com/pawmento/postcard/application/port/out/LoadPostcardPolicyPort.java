package com.pawmento.postcard.application.port.out;

import com.pawmento.postcard.domain.PostcardPolicy;

public interface LoadPostcardPolicyPort {
  PostcardPolicy load();
}
