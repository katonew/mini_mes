package com.taehyung.mes.common.actor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 인증 사용자 대신 개발용 시스템 식별자를 제공한다. */
@Component
public class EnvironmentActorProvider implements ActorProvider {
    private final String actorId;

    public EnvironmentActorProvider(@Value("${mes.actor.id:}") String actorId) {
        this.actorId = actorId;
    }

    @Override
    public String getActorId() {
        return actorId;
    }
}
