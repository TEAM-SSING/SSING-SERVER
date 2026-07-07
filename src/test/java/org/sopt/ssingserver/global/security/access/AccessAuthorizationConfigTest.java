package org.sopt.ssingserver.global.security.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

class AccessAuthorizationConfigTest {

    @Test
    void addInterceptors는_RequireAccessInterceptor를_전체_경로에_등록한다() {
        RequireAccessInterceptor requireAccessInterceptor = mock(RequireAccessInterceptor.class);
        AccessAuthorizationConfig config = new AccessAuthorizationConfig(
                requireAccessInterceptor,
                mock(CurrentMemberArgumentResolver.class)
        );
        TestInterceptorRegistry registry = new TestInterceptorRegistry();

        config.addInterceptors(registry);

        assertThat(registry.interceptors()).containsExactly(requireAccessInterceptor);
    }

    @Test
    void addArgumentResolvers는_CurrentMemberArgumentResolver를_등록한다() {
        CurrentMemberArgumentResolver currentMemberArgumentResolver = mock(CurrentMemberArgumentResolver.class);
        AccessAuthorizationConfig config = new AccessAuthorizationConfig(
                mock(RequireAccessInterceptor.class),
                currentMemberArgumentResolver
        );
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

        config.addArgumentResolvers(resolvers);

        assertThat(resolvers).containsExactly(currentMemberArgumentResolver);
    }

    private static class TestInterceptorRegistry extends InterceptorRegistry {

        List<Object> interceptors() {
            return getInterceptors();
        }
    }
}
