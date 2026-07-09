package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MatchingOfferExpirationTriggerServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-07T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private MatchingOfferRepository matchingOfferRepository;

    @Mock
    private MatchingOfferExpirationService matchingOfferExpirationService;

    @Test
    void expireOverdueOffers는_무기한정책이면_만료대상을_조회하지_않는다() {
        MatchingOfferExpirationTriggerService service = createService(new MatchingTimeoutPolicy());

        service.expireOverdueOffers();

        verifyNoInteractions(matchingOfferRepository);
        verifyNoInteractions(matchingOfferExpirationService);
    }

    @Test
    void expireOverdueOffers는_유한정책이면_만료대상_제안을_조회하고_각_제안을_처리한다() {
        MatchingOfferExpirationTriggerService service = createService(finiteTimeoutPolicy());
        when(matchingOfferRepository.findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                MatchingOfferStatus.OFFERED,
                FIXED_CLOCK.instant(),
                PageRequest.of(0, 100)
        )).thenReturn(List.of(10L, 11L, 12L));
        doAnswer(invocation -> null)
                .when(matchingOfferExpirationService)
                .expireOffer(anyLong());

        assertThatCode(service::expireOverdueOffers).doesNotThrowAnyException();

        verify(matchingOfferExpirationService).expireOffer(10L);
        verify(matchingOfferExpirationService).expireOffer(11L);
        verify(matchingOfferExpirationService).expireOffer(12L);
    }

    private MatchingOfferExpirationTriggerService createService(MatchingTimeoutPolicy matchingTimeoutPolicy) {
        return new MatchingOfferExpirationTriggerService(
                matchingOfferRepository,
                matchingOfferExpirationService,
                matchingTimeoutPolicy,
                FIXED_CLOCK
        );
    }

    private MatchingTimeoutPolicy finiteTimeoutPolicy() {
        return new MatchingTimeoutPolicy() {
            @Override
            public boolean shouldRunInstructorOfferExpiration() {
                return true;
            }
        };
    }
}
