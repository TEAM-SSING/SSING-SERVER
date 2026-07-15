package org.sopt.ssingserver.domain.matching.event;

public interface MatchingEventHandler {

    void handle(MatchingDomainEvent event);
}
